# 后台会话预处理设计

> 状态：用户已确认，待规格审阅
> 分支：`newmp`
> 前置提交：`ffdbb9f` 后台策略快照

## 目标

让真实 `ChatRoute` 在用户消息已被现有发送协调器成功持久化后，调用一个非冻结预处理入口，取得已归一化的教学策略和安全上下文证据，并只创建一个后台生成任务。WorkManager 仍是唯一 LLM 执行器；`SessionConversationAssembly.runTurn()` 的直接生成路径不在本切片调用。

## 方案

新增 `BackgroundTurnPreparationPort` 作为 `feature:chat` 到 app/wiring 的唯一入口。它接收已保存消息的稳定标识、用户文本、引用和图片附件，返回安全的 `BackgroundTurnPreparation`：`BackgroundGenerationInput`、加载态 `SessionConversationContract`、以及 jobId 之外必要的安全状态。

生产实现位于 `app/wiring/session`，复用 `ConversationContextAssembler` 与 `SessionTurnPolicy`，但不复用会保存用户消息且会直接生成的 `ConversationSessionCoordinator`。预处理器只做：会话/token 可用性检查、上下文聚合、策略归一化、将策略映射为获批的后台任务快照、入队。它不写第二条用户消息、不调用 `ChatGenerationRepository`、不运行 Worker、不构造 Compose 类型。

## 调用顺序

```text
ChatRoute
  → 现有 ChatSendCoordinator.send（唯一的用户消息写入）
  → BackgroundTurnPreparationPort.prepareAndEnqueue（上下文、策略、入队）
  → BackgroundGenerationWorker.enqueue(jobId)
  → WorkManager Worker（唯一 LLM 生成和 assistant 写入）
```

对 UI 而言，入队成功后是 `LOADING`，并携带本轮 action、evaluation、next step 和安全 context；后台 job 终态仍沿用现有轮询与 reload。会话删除、token 陈旧、重复入队、无模型和失败都只显示已有安全状态，不能重复生成或泄露 Provider 信息。

## 边界

- 本轮修改：非冻结 `feature:chat` port 类型与测试、`app/wiring/session` 预处理实现与测试、`HybridAppGraph` factory、`AppShell` 到 `ChatRoute` 的 port 传递、`ChatScreen.kt` 的入队调用点。
- 不改：`core:model`、`core:protocol`、`core:llm`、`core:data`、Room、DAO、Worker、通知、PWA、Python、签名。
- 不改：并行 Track B 正在编辑的 `SessionAssistantPanel`、`SessionAssistantReplyHint` 和它们的测试。

## 失败策略

- 若用户消息写入失败，不调用预处理器或入队。
- 若预处理判定会话已删除、token 已陈旧或输入为空，不入队且返回安全契约。
- 若局部上下文来源失败，保留可用类别并通过既有白名单 warning 告知；不阻塞策略。
- 预处理器不检查 Provider 可用性；无模型与 Provider 失败由后台任务的既有终态路径处理。

## 验收

1. 一次发送只产生一条用户消息、一个后台 job、一次 Worker 调度。
2. job 中的策略快照与本轮契约 action/evaluation 对应；重启后 job 仍使用同一快照。
3. ChatRoute 不再直接构造 `BackgroundGenerationInput` 的策略/上下文，不直接调用 LLM。
4. session/token 失效不入队；没有 assistant 重复写入。
5. 端口与协调器 JVM 测试通过；全量 Android/Python 回归只在并行 Track B 工作提交或隔离后运行。
