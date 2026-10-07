# NEWMP-V1-013 — 掌握度读模型：学习台账确定性重放（mastery read-model）

日期：2026-09-10
状态：已完成（代码已推送，测试全绿，git 提交见提交记录）
对齐矩阵：gap 6（mastery projection）

## 1. 目标

关闭对齐矩阵 gap 6：旧主线 engine.py 的 `upsert_mastery` 掌握度语义在新主线缺位。本任务把「掌握度」实现为**学习台账（append-only LearningFactReceipt ledger）之上的确定性读模型（projection）**，不改冻结层（core:model / protocol / llm / data、Room schema/DAO/migration、SecretStore）。

## 2. 方案（与旧 engine.py 语义逐项对齐）

纯函数 `MasteryLedgerProjection.project(facts)` 按 knowledgePoint 分组、按（occurredAt, sourceTurnId）稳定排序后逐条重放：

- 证据门控：`evidenceType == "none"` 或未知类型 → 该条目不更新分数（attempts 仍计数）；
- EMA 更新：`score = round2(0.65 × old + 0.35 × target)`，clamp [0, 100]；
- partial：target × 0.75 后进 EMA；failed：score > 50 才回滚 8 分（否则不变），interval 归 1；
- 复习梯子：[1, 3, 7, 14] 天（normal 口径），首个大于当前值的梯级，封顶最后一级；
- `nextReviewAt` 锚定 **fact.occurredAtEpochMillis + interval 天**（不锚墙钟）——重放是纯函数、可复算；
- band 为稳定英文码：untouched / intuitive_entry / guided_example / basic_application / variant_handling / transferable（中文文案留在 UI 层，遵循主线章程）；
- 证据目标分：explanation 35 / retrieval 55 / transfer 72 / delayed_retrieval 82 / correction 90。

装配链路：`MasteryFactContextPort`（core:domain 接口）→ `MasteryFactContextPortAdapter`（space 级口径，与 Memory/Source 适配器同款已记录 caveat）→ `ConversationContextAssembler` 经 `safeRead` 接入 `masteryProjections`（缺 port / 失败均降级为空，不产生 warning 噪音）→ `SessionPolicyInputMapper` 把 snapshot 映射为 `kind="Mastery"` 的 LlmContextEvidence（复用 MaxContextEvidence=6 上限，冻结的 LlmGenerationPlanner 不动）。

## 3. 落点（8 个文件）

1. `core/domain/src/main/.../MasteryLedgerProjection.kt` — 新增核心读模型；
2. `core/domain/src/main/.../ConversationContextContracts.kt` — contract 增加 `masteryProjections`（带默认值，向后兼容）+ `MasteryFactContextPort` 接口；
3. `core/domain/src/main/.../ConversationContextAssembler.kt` — safeRead 接入；
4. `core/domain/src/main/.../SessionPolicyInputMapper.kt` — snapshot → evidence 映射；
5. `core/domain/.../ConversationContextPortAdapters.kt` — 适配器实现；
6. `app/.../SessionConversationAssembly.kt` — 装配参数；
7. `app/.../HybridAppGraph.kt` — 复用已提升的 `LearningLedgerRepository` 实例接线；
8. 测试三件套（见 §4）。

## 4. 测试证据（TDD：Red → Green）

- **Red**：先写测试，编译失败于未解析引用 `MasterySnapshot` / `masteryProjections`（gradle_test 失败退出，语义为 Red 成立）；
- **Green**：`BUILD SUCCESSFUL`（exitCode 0，1m18s），三个套件 XML 证据（build/test-results/testDebugUnitTest/）：
  - `MasteryLedgerProjectionTest`：15/15（种子、partial 折减、EMA 序列折叠、failed 回滚双分支、梯子推进与封顶、none/未知不更新、知识点隔离与排序、空 kp 过滤、乱序输入确定性、projectDue 到期筛选、band 全阈值、snapshotLimit、空台账）；
  - `ConversationContextAssemblerTest`：14/14（新增 3：port 接通投影、port 抛错只降级 mastery、port 缺省无 warning）；
  - `SessionPolicyInputMapperTest`：8/8（新增 1：mastery → evidence 映射）。
  - failures=0 / errors=0 全套件。

## 5. 边界与不做

- **不声明** write-side `upsert_mastery` 已迁移——本任务只是读模型；写侧仍由台账事实流驱动（后续如需 upsert 语义属于另一个任务）；
- 适配器为 **space 级口径**（`listLearningFacts(spaceId)`），session 级过滤未做（与 Memory/Source 适配器同款 caveat，已在代码注释与对齐矩阵记录）；
- 旧 engine 的 `review_frequency`（high ladder [1,2,4,7]）参数未迁移，只落 normal 梯子；
- 浮点断言对多步期望用 ±0.011~0.021 delta，规避 alpha=0.35 产生的 .xx5 舍入 tie 在 Python（banker's）与 Kotlin（HALF_UP）之间的口径差。

## 6. 后续

- 到期复习调度（gap 7）可直接复用 `projectDue(facts, now)`；
- UI 层把 band 英文码映射中文文案（六档：未接触/有直观入口/能跟着例子讲/能基础应用/能处理变式/可迁移纠错）；
- write-side 融合（如真需要旧 upsert 的即时写回）另立任务评估。
