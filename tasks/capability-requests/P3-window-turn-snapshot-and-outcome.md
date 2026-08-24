# 冻结层能力申请：P3 窗口回合快照与结构化回合结果

> 状态：**proposed，未批准，不得实施**
>
> 对应切片：`newmp` 原生 Android 生产分支 —— `native-companion-memory-topology`（Task 11）
> 申请日期：2026-08-24
> 关联文档：[native-companion-memory-topology-design.md](../native-companion-memory-topology-design.md) §6/§7、[native-backend-protocol-data-contract-freeze.md](../native-backend-protocol-data-contract-freeze.md)、[native-companion-old-main-parity-matrix.md](../native-companion-old-main-parity-matrix.md)
> 约束：**必须与 P6（window 拓扑/内存/heartbeat 持久化）分开获批**。本申请只授权「生成协议」侧的可选字段；不含 Room 表、DAO、migration、SecretStore 或后台 job 载荷变更。

## 1. 能力缺口（源码事实）

当前生产回合路径是单一写入者：

```text
用户消息 -> ChatSendCoordinator（唯一 user 写入者）
  -> BackgroundTurnPreparationCoordinator -> BackgroundGenerationInput（冻结）
  -> BackgroundGenerationWorker（唯一 Provider 调用 + assistant 写入）
       -> BackgroundGenerationRepository.runGenerationJob()
       -> ChatGenerationRepository.generateReply()
  -> token/session 校验 -> assistant 记录
```

Worker 消费的是冻结 `BackgroundGenerationInput`/`ChatGenerationInput`（`contextEvidence` + `sessionPolicy`）。对照矩阵（§2 行 1/2/7/9）明确：当前 Worker **不**运行旧 `engine.py` 的提示词装配，也**不**运行回合后的 master/内存投影。这些属于本次拓扑化的目标行为，但底层生成协议没有承载它们的载体：

- 没有「不可变窗口/topology 上下文」字段（窗口 id、root id、父 id、fork 快照 revision、window kind）。
- 没有结构化 `TurnPlan` 输入（turn 意图、动作、角色、知识点的归一化 `LlmSessionPolicyContext` 之外的稳定字段）。
- 没有 `InitiativePlan` 来源字段（heartbeat/initiative 生成的回合需标记来源，避免与用户回合混淆）。
- 没有后置的、已验证的 `StructuredTurnOutcome` 输出（回合归一化评价、归一化动作、master 证据、窗口归属、处理过程摘要）。

`idempotent per job` 需要把以上快照在 job 生成时固化，且重试不得读取更新的内存；当前字段均不可变固化这些内容。

## 2. 最小提案（待审批后再细化）

在冻结层新增**向后兼容的、可选（默认 null）**字段；不改变任何既有必填字段与现有行为。落位：

```text
core/llm  (LlmGenerationLifecycle.kt)
  - 可选 immutable window/topology context（稳定 wire 字符串 + revision，无 UI/DTO）
  - 可选 TurnPlan（动作/角色/知识点/难度等归一化快照）
  - 可选 InitiativePlan source（枚举/标志，标记 initiative 生成来源）
  - 可选 StructuredTurnOutcome（post-turn 投影输出，经校验）

core/data/llm  (ChatGenerationInput)
  - 转发上述可选字段（默认 null）

core/data/background  (BackgroundGenerationInput / runGenerationJob)
  - 转发上述可选字段，并在 job 生成时固化（snapshot 不随重试重新读取）
```

不涉及：
- `core/model`、`core/protocol`（无枚举/协议 schema 改动）。
- Room entity、DAO、schema、migration。
- `SecretStore`、导入/导出、后台 job 持久化载荷表结构（idempotency 相关持久化由 P6 单独获批）。

### 2.1 载荷边界

- `TurnPlan`：有限长度、白名单化 wire 字符串（动作∈study/goal/companion 白名单，角色∈`StudentRoleWire.ALL`），难度 clamp `0f..1f`，知识/错误/迷思有字符上限，复用 `SessionTurnContracts` 的 bounded 归一化。
- `StructuredTurnOutcome`：只含归一化评价（correctness/depth clamp `0f..1f`）、归一化动作、master 证据（`none`/passed/partial/failed）、窗口归属与处理摘要；**绝不携带原始 Provider 文本或原始用户文本**。
- 上下文/topology 上下文：`windowId`/`rootId`/`parentId?`/`WindowKind` wire 值 + 不可变 `forkRevision`，纯字符串+Long，无 Entity/DAO。

### 2.2 向后读取行为

- 所有新字段默认 `null`。旧 job / 旧调用方缺省字段时行为与今天完全一致：planner 不注入新块，outcome 缺省为安全 no-op（`StructuredTurnOutcome.empty`）。
- 无字段被重命名为必填；无协议 schema 顶层字段新增（这是生成协议内部载荷，不进入 `VersionedProtocolSchemas.all`）。

### 2.3 失败映射

- 任何畸形/未知的新字段值 → 安全 no-op：归一化为默认值或空 outcome，绝不反序列化出 raw Provider 文本。
- `StructuredTurnOutcome` 校验失败 → 记录安全失败码（复用 `SessionTurnContracts.safeGenerationFailureCode`），不写入 assistant 记录、不透传 Provider 错误详情。

### 2.4 无 raw transcript 重复

- 新字段中无 `raw transcript`、无 `raw message text`、无 Authorization/Bearer/URL/密钥。所有文本经 `sanitizeContractText` 与白名单归一化。
- `StructuredTurnOutcome` 不复制用户/assistant 原始内容；只承载归一化值。

## 3. 冻结范围与影响

| 区域 | 可能变更 | 当前状态 |
|---|---|---|
| `core/llm` | `LlmGenerationLifecycle.kt` 可选字段 + planner 注入 + outcome 校验 | 未批准 |
| `core/data/llm` | `ChatGenerationInput` 转发可选字段 | 未批准 |
| `core/data/background` | `BackgroundGenerationInput`/`runGenerationJob` 快照固化 | 未批准 |
| `core/model` | 无 | 不触及 |
| `core/protocol` | 无 | 不触及 |
| `core/data/local` | 无（idempotency 持久化归 P6） | 不触及 |
| `SecretStore` | 无 | 不触及 |

## 4. 兼容、测试与回滚

### 测试计划

- 序列化/兼容：缺省新字段的旧 job 可读、行为不变。
- 快照固化：新 job 跨重启保留不可变窗口快照；重试不读取更新的内存。
- 校验失败：畸形结构化输出映射为安全 no-op/失败码，不透传 Provider 详情。
- 单写入者不破坏：`BackgroundGenerationWorker` 仍只调 `BackgroundGenerationRepository.runGenerationJob`，`PostTurnProjector` 仅在校验通过后按 job id 幂等投影。
- 回归：`core:llm` / `core:data` / `app` JVM 测试全绿；不引入真实 Provider 调用、不读取生产 API key。

### 回滚

- 字段可选、默认 null。回滚 = revert 提交；无 schema/DAO/migration 参与。
- 若已固化 job 快照含新字段，降级读取策略：缺省字段时按旧行为运行，丢弃不可识别的可选字段。

## 5. 审批门禁

- [ ] 用户批准该 P3 生成协议变更方向（本申请）
- [ ] 与 P6 申请分开批准（本申请不含 Room/DAO/migration/job 表变更）
- [ ] 审批后：单独提交完整变更说明（受影响文件、协议字段、兼容、测试、回滚）后，才进入 Task 11 实现

在上述勾选完成前，`BackgroundGenerationWorker`/`ChatGenerationRepository` 保持现有可选字段行为不变。

## 6. 计划自检（本申请是否完全冻结外行为）

本申请不包含：`WindowConversationContract`（feature:chat，非冻结）、`WindowTopologyPolicy`/`CompanionMemoryEvolutionPolicy`/`LearningScopeGuard`/`InitiativeEligibilityPolicy`（core:domain，非冻结）、`WindowConversationAssembly`（app wiring，非冻结）。这些在 Package A/B 已实现且各自有测试；本申请只为把它们落到真实 Worker 生成协议提供可持久化的承载。
