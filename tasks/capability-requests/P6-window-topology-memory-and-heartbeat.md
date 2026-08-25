# 冻结层能力申请：P6 窗口拓扑、内存与心跳持久化

> 状态：**proposed，未批准，不得实施**
>
> 对应切片：`newmp` 原生 Android 生产分支 —— `native-companion-memory-topology`（Task 10/12）
> 申请日期：2026-08-24
> 关联文档：[native-companion-memory-topology-design.md](../native-companion-memory-topology-design.md) §2/§5/§8、[native-backend-protocol-data-contract-freeze.md](../native-backend-protocol-data-contract-freeze.md) §5、[native-companion-old-main-parity-matrix.md](../native-companion-old-main-parity-matrix.md)
> 约束：**必须与 P3（生成协议）分开获批**。本申请只授权「持久化」侧的表/仓库/迁移；不含 `core/llm` 生成协议、`core/model`/`core/protocol` 枚举或协议 schema 变更。

## 1. 能力缺口（源码事实）

当前 `ReverseTutorDatabase.version = 6`，且已有 `migration1To2`、`migration2To3`、`migration3To4`、`migration4To5`、`migration5To6`。现有表集合覆盖会话/消息/来源/图谱/记忆项/后台任务等，但没有以下可持久化载体：

- **窗口树**（root/child、rootId、parentId、kind）：`SessionConversationAssembly` 与 `WindowConversationAssembly`（Package B）只做了纯投影，接口层没有窗口树存储，只能靠每次从读端口 fakes 注入。
- **不变 fork 快照元数据**（ancestorRevision、forkedAtEpochMillis）：Task 2 的 `WindowSnapshotRef` 仅存在于内存领域类型。
- **窗口局部 delta**：尚未有窗口级增量存储。
- **merge receipt + idempotency key**：Task 2 的 `MergeCommit`（`id`, `childId`, `parentId`, `deltaId`, `sourceRevision`）需要持久化且可幂等判定。
- **归一化学习 ledger receipt**：Task 3 的 `LearningFactReceipt` 目前无表。
- **最小 scope signal**：Task 4 的 `ScopeSignal(category, count)` 需要结构化存储，不能存 transcript。
- **companion-memory versions**：Task 3 的 `ActiveMemoryVersion`/`MemoryObservation` 需要持久化并保留最小 provenance，供诊断/幂等/修复。
- **heartbeat job**：Task 5 的 `HeartbeatScheduleContract`/`EnableWindowHeartbeatCommand` 需要可落库；否则「root 默认开启、child 显式开启」无法持久.

设计 §2.2/§3.1 明确：ledger 只收结构化的学习事实源（knowledge point/evidence type/result/confidence/source window/source turn/occurred time），**绝不含原始对话、个人/关系推断或节奏推断**。设计 §10 保持最小内省 provenance。

## 2. 最小提案（待审批后再细化）

将上一节各类数据**分别按 aggregate 切块**持久化，每块都满足：`spaceId` + root/window 归属、外键安全删除、需要时带 idempotency key、**无 raw transcript 列**。

窗口身份采用既有会话身份：`WindowRef.id == Session.id`，即 `windowId` 直接复用现有 `sessionId`。根窗口的 `rootId == sessionId`；子窗口的 `parentId` 是直接父会话 id。不得新建可漂移的 window-to-session 映射表。迁移对已有 session 一律建立 `TASK_ROOT` 拓扑行；不从历史内容推断 companion/personality。新的 companion root 只能通过明确创建流程建立。

列表（每个聚合独立切片，审批后再定表名/列/索引细节——本申请不预设 schema 实现、不授权立即改 Room）：

```text
A. 窗口树：WindowEntity(sessionId PK/FK -> existing Session, spaceId, rootId, parentId?, kind, createdEpochMillis)
   + 不变 fork 快照元数据：WindowSnapshotEntity(windowId PK, ancestryRevision, forkedAtEpochMillis)
   + 窗口局部 delta：WindowDeltaEntity(id, windowId, deltaId, payloadHandle, sourceRevision)
B. merge receipt + idempotency key: MergeCommitEntity(id PK, childId, parentId, deltaId, sourceRevision, spaceId)
   （idempotency 键 = childId|parentId|deltaId|sourceRevision，与 Task 2 mergeCommitId 一致）
C. 归一化学习 ledger: LearningFactReceiptEntity(id, spaceId, knowledgePoint, evidenceType, result,
   confidence, sourceWindowId, sourceTurnId, occurredAtEpochMillis)
D. 最小 scope signal: ScopeSignalEntity(id, windowId/sessionId, spaceId, category, count,
   sourceTurnId, occurredAtEpochMillis)
   （无 transcript/无监控文本列；时间和回合标识仅用于有限窗口内判断“持续”，不保存用户原文）
E. companion-memory versions: CompanionMemoryVersionEntity(partition, windowId, spaceId, value,
   origin, promotedAtEpochMillis, revision) + MemoryObservationEntity(...)（含 provenance handle，
   【无】raw message text 列）— 复用 Task 3 `ALLOWED_PERSISTED_FIELDS`
F. heartbeat jobs: WindowHeartbeatEntity(windowId PK, spaceId, enabled, minCooldownMillis) + 可选
   HeartbeatJobEntity（cooldown/持候/派发状态）— 根默认 enabled，子默认 disabled
```

### 2.1 迁移顺序（一次性建议）

1. `migration(6 -> 7)`：表 A + B（窗口树/快照/局部 delta/merge receipt）并为现有 session 创建安全的 `TASK_ROOT` 行。
2. `migration(7 -> 8)`：表 C + D（learning ledger / 有发生时间的 scope signal）——独立于 companion 内存域。
3. `migration(8 -> 9)`：表 E + F（companion-memory versions / heartbeat jobs）。
4. 每步：bump `DatabaseSchema.version`、补充 `Migration`、导出 schema JSON、补 migration test。禁止 destructive migration；新列/表优先 nullable/默认值，避免破坏旧安装升级。禁止删除已有 `migration1To2`。

### 2.2 Repository 只出 domain-safe 模型

- 新增 Repository 方法只读/写 Package A 契约类型（WindowRef/WindowSnapshotRef/MergeCommit/LearningFactReceipt/ScopeSignal/ActiveMemoryVersion/HeartbeatScheduleContract），UI/feature 永不接收 Entity/DAO。
- `Delete` 语义遵循 Task 2 `BranchDeletionEffect`：删除分支只删其局部会话/局部内存；不回转已提交的父 merge，不清除全局 learning receipt。设计 §2.3/§1.7。

## 3. 冻结范围与影响

| 区域 | 可能变更 | 当前状态 |
|---|---|---|
| `core/data/local` | Entity/DAO/`DatabaseSchema`/`ReverseTutorDatabase`/migration（版本 6→7→8→9，导出 schema JSON） | 未批准 |
| `core/data/*Repository` | 新增 topology/memory/scope/ledger/heartbeat Repository 方法（只出 domain-safe 模型） | 未批准 |
| `core/data/preferences` | 无（heartbeat 由表而非偏好 key 表达） | 不触及 |
| `SecretStore` | 无 | 不触及 |
| `core/model`/`core/protocol` | 无（不新增枚举/协议 schema 顶层字段） | 不触及 |
| `core/llm` | 无（生成协议归 P3） | 不触及 |

## 4. 兼容、测试与回滚

### 测试计划（migration 先于 schema 代码）

- 根默认 heartbeat enabled、子默认 disabled。
- fork 时父 revision 不可变；仅直接父可 merge；重复 merge 幂等。
- 分支删除保留父 merge 与全局学习 receipt；删除仅清局部。
- 无 companion-memory 行对 task-root 查询可见（domain 隔离）。
- Repository contract test：`spaceId` 归属、外键删除、idempotency key、跨空间隔离。
- 结构安全：`LearningFactReceiptEntity`/`MemoryObservationEntity`/`ScopeSignalEntity` 无 raw transcript 列（与 Task 3/4 `ALLOWED_PERSISTED_FIELDS` 对齐）。
- JVM：`:core:data:testDebugUnitTest` 全绿。
- Instrumentation：每台可用设备跑一次 `:core:data:connectedDebugAndroidTest`。若环境阻塞，如实记录阻塞原因并**不**用 JVM pass 替代。

### 回滚

- Room 数据库只做前向迁移，不通过降低 schema version 回滚。发布后发现问题时，以 feature-disable/no-op 读取路径关闭新能力，同时保留 6→7→8→9 migration，避免已升级安装无法重新打开。
- 合并/心跳/内存/ledger 表均为新增表；代码回滚必须保留已发布 migration 的兼容读取。已有 `migration1To2` 到 `migration5To6` 全部保留。
- 若引入破坏性数据变更（本申请未包含），需先说明与提供数据迁移备份。

## 5. 审批门禁

- [ ] 用户批准该 P6 持久化变更方向（本申请）
- [ ] 与 P3 申请分开批准（本申请不含生成协议载荷）
- [ ] 审批后：按本文 §2.1 迁移顺序，单独提交每个 aggregate 的变更说明（受影响文件、schema、兼容、测试、回滚）后，才进入 Task 10/12 实现

在上述勾选完成前，`WindowConversationAssembly` 的 `prepareDispatch` 保持「尚未持久化 until P6」，同 Session 只读端口/假运行时不写入任何新表。

## 6. 计划自检（本申请是否完全冻结外行为）

本申请不包含：`WindowTopologyPolicy`/`CompanionMemoryEvolutionPolicy`/`LearningScopeGuard`/`InitiativeEligibilityPolicy`（core:domain，非冻结，Package A 已实现）、`WindowConversationContract`/`WindowConversationFacade`（feature:chat，非冻结，Package B 已实现）、`WindowConversationAssembly`（app wiring，非冻结，Package B 已实现）。本申请只为把这些领域契约提供**经批准的正规持久化**，避免 UI 绕过 Repository 直接读 DAO/Entity。
