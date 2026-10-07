# 后台会话策略快照设计

> 状态：用户已批准冻结层变更，待规格审阅
> 分支：`newmp`
> 关联：`P3-session-policy-context.md`、`SessionConversationAssembly`、后台生成通知与恢复机制

## 目标

让真实聊天继续使用现有 WorkManager 后台生成、恢复、取消和通知能力，同时把一次会话在入队时已经归一化的教学策略稳定地带到后台执行和进程重启后的恢复执行中。每一次生成只能有一个执行器：后台任务；不得同时调用 `runTurn()` 的直接生成和 `BackgroundGenerationRepository.runGenerationJob()`。

## 当前事实与缺口

前台直接生成链路已经能够把 `SessionPolicyOutput` 映射为可选的 `LlmSessionPolicyContext`，由 `ChatGenerationInput.sessionPolicy` 进入 LLM planner。

后台链路已经能持久化模型绑定、引用、图片附件和 `contextEvidence`，但 `BackgroundGenerationInput`、`BackgroundGenerationJob`、`BackgroundJobEntity` 和恢复时重建的 `ChatGenerationInput` 均没有策略字段。因此后台任务会绕过策略提示词，并会在重启恢复后丢失入队时的教学决策。

## 方案选择

采用“持久化已归一化策略快照”。不在 Worker 中重新计算策略，不在 Compose 中构造 LLM 输入，也不以当前数据库状态重新推导旧任务的策略。

原因：策略重算会因上下文、设置或数据变化而产生不同结果；直接生成会破坏已验证的后台恢复/通知/取消路径；仅在 UI 里展示策略却不传给 LLM 会造成展示与实际生成不一致。

## 数据与兼容性

### 允许持久化的字段

每个后台生成任务增加可空 `sessionPolicyPayload`，对应一个已边界化的 `LlmSessionPolicyContext`：

- `actionType`
- `studentRole`
- `knowledgePoint`
- `difficulty`
- `processSummary`
- `evaluationCorrectness`
- `userEmotion`
- `correctionTiming`

写入前必须复用 `LlmSessionPolicyContext.normalized()` 的边界；采用与既有 evidence payload 一致的转义行格式。快照不包含 API key、Authorization、Provider/URL、原始错误、用户完整消息、完整上下文、DAO/Entity 或 SecretStore 引用。

### Room 迁移

数据库版本从 5 升至 6，`migration5To6` 仅执行：

```sql
ALTER TABLE background_jobs ADD COLUMN sessionPolicyPayload TEXT
```

已有任务得到 `NULL`。`NULL` 的含义固定为旧行为：重建 `ChatGenerationInput` 时传 `sessionPolicy = null`，不增加策略提示词，不影响恢复、通知、取消或旧导入的数据。

不新增表、不改 DAO 查询、不改 `core:model`、不改 SecretStore、不开启 destructive migration。

## 运行链路

```text
聊天宿主（只发事件）
  → 非冻结的后台会话协调器
    → 汇聚上下文 + 运行 SessionTurnPolicy
    → 接受用户消息与 turn/token 门禁
    → BackgroundGenerationInput(policySnapshot, contextEvidence)
    → BackgroundGenerationRepository 入队并持久化快照
    → Worker 读取 jobId
    → runGenerationJob 重建 ChatGenerationInput(sessionPolicy = snapshot)
    → 现有 LLM planner / 现有 assistant 持久化 / 现有通知处理
```

最终 `SessionAssistantPanel` 只消费由协调器/Facade 映射出的 `SessionConversationContract`。其加载态、成功态、无模型、失败、陈旧 token 与会话删除均不得由 UI 或 Worker 自行推导。

## 所有权与文件边界

冻结层（本次已获批准、只做最小改动）：

- `core:data/background/BackgroundGenerationRepository.kt`
- `core:data/local/entity/Entities.kt`
- `core:data/local/DatabaseSchema.kt`
- Room schema export

非冻结装配层：

- 新建或扩展 `app/.../wiring/session/` 的后台会话协调器/适配器；
- `app/shell/AppShell.kt` 只将聊天事件交给该协调器，并继续以 jobId 调度 Worker；
- `feature:chat` 只消费 `SessionConversationContract` 与交互回调。

不修改 `core:model`、`core:protocol`、`core:llm`、DAO、`BackgroundGenerationWorker` 的 WorkManager 输入格式、通知策略、PWA、Python 后端或签名。

## 失败与回滚

- 解码失败或非法快照：以 `null` 处理，采用旧行为；不得让任务崩溃或显示原始 payload。
- 会话删除、token 陈旧、取消和重复 job：完全保留既有判定与状态机语义。
- Provider 失败：继续由既有安全失败映射和诊断处理；快照不得进入通知文本或诊断原文。
- 回滚：回退应用代码仍可读取 6 版数据库的新增列；不执行降级迁移。发布回滚以兼容新列的应用版本为准。

## 验收标准

1. 新任务入队后，策略快照可读回并经重启恢复到同一 `ChatGenerationInput.sessionPolicy`。
2. `NULL` 旧任务运行结果与改动前一致，且不包含策略提示词。
3. 策略快照通过既有 LLM planner 测试，但测试绝不请求真实 Provider。
4. 会话删除、token 失效、取消、失败、完成、通知和启动恢复的既有测试继续通过。
5. Room 5→6 迁移保留旧任务、允许入队新任务，schema export 更新。
6. `SessionAssistantPanel` 的 UI 状态不泄露任何敏感字段，也不生成第二次请求。
7. 完成后跑核心数据、LLM、app、feature:chat 测试，完整 Android 构建/Lint、Python 回归、`git diff --check` 和冻结层差异审查。
