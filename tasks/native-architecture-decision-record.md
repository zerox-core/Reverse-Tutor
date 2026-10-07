# Wave 1 · B0 架构裁决记录（Architecture Decision Record）

> 生成时间：2026-08-14（周五）
> 执行人：本地开发搭档（feishu_mcp）
> 总纲：v2.1 审计结论与落地计划（`tasks/audit-conclusion-and-plan.md`）
> 任务范围：只做 B0，完成后停止；不开始 B1，不改业务代码。
> 验收标准：本文件成为 B1–B6、Track A/Track B 与 capability request 的共同判定依据。

## 1. 决策状态与权威来源

**状态：Accepted。**

**Android 基线：** 分支 `Android`，HEAD `d6b6186`，远端 `origin/Android` 已同步，工作树干净。本裁决记录以该基线为准。

**权威关系（自上而下，上层不可被下层推翻）：**

| 层级 | 文件 | 权威范围 |
|---|---|---|
| 0 · 协作硬规则 | `AGENTS.md` | 不可违反的协作硬规则：不确定性停机、环境（Windows/PowerShell/`py`）、测试（pytest 全绿）、代码改动（最小上游修复、安全默认值）、签名铁律、冻结层边界（§0.1）。任何后续文档与实现不得与之冲突。 |
| 1 · 冻结层与审批边界 | `tasks/native-backend-protocol-data-contract-freeze.md`（2026-07-04） + `tasks/wave0-a3-freeze-boundary.md` | 冻结层清单（后端协议接口层 + 数据库/数据层）、冻结范围覆盖 Wave 2、变更说明模板、审批流程、不允许混在 UI 重构中的改动清单、验证基线测试入口。 |
| 2 · Wave 编排与验收门禁 | `tasks/audit-conclusion-and-plan.md`（v2.1） | Wave 0–4 编排、双轨分工、垂直切片集成门禁、B0–B6 排序与完成标准、不可逆约束、capability request 流程总纲。 |
| 3 · 本裁决记录 | `tasks/native-architecture-decision-record.md`（本文件） | P1–P7 术语裁决、Repository/Coordinator/Facade 职责与调用方向、Track A/B 边界引用、冻结层变更裁决引用、capability request 标准流程与最小字段。 |

**源码即签名的唯一权威：** 本裁决记录定义术语、职责边界和协作流程，不手抄 Kotlin 方法签名。精确签名（方法名、参数类型、返回类型、枚举值）以 `mobile-native/` 下的 Kotlin 源码为唯一权威，文档不得替代源码签名。B1 代码事实盘点将从源码提取 Repository/Gateway/Transport/消费者/实现者清单；B2 契约拓扑落仓时记录契约 ID、权威源码路径、行为不变量、消费者、测试位置，精确签名仍以 Kotlin 源码为准。任何文档中的签名摘录若与源码冲突，以源码为准并更正文档。

---

## 2. P1–P7 术语裁决

以下术语统一为后续 B1–B6、Track A/Track B 和 capability request 的共同判定依据。**禁止再出现「P5 Coordinator」**；Coordinator 统一归 P2 / `core:domain` 编排层。

| 协议 | 定义者 | 消费者 | 实现者 | 职责 | 变更门禁 |
|---|---|---|---|---|---|
| **P1 · Domain Model Contract** | `core:model`（冻结） | `core:data`、`core:protocol`、`core:llm`、`core:domain`、Feature/ViewModel、UI（经 UiState 映射） | `core:model`（冻结） | UI 与数据层之间的稳定类型边界：`TutorSession`/`Message`/`MessageRole`/`LlmProfile`/`LlmProviderKind`/`MemoryItem`/`GraphNode`/`SourceRecord`/`BackgroundJob`/`Space` 等 domain model 与枚举值。 | 冻结层门禁：新增/删除/修改枚举值或类型签名须走 A3 变更说明 + 测试方案 + 用户批准。中文文案在 UI 层映射，不改 enum。 |
| **P2 · Domain Access and Coordination Contract** | `core:domain`（Track A 契约层，非冻结） + `core:data/*Repository`（冻结） | Feature ViewModel / Facade / Coordinator 消费者 | `core:domain`（Coordinator contracts） + `core:data`（Repository 实现，冻结） | Repository 单一数据能力入口（Session/Message/LLM Profile/Chat Generation/Background Generation/Import/Export/Source/Memory/Graph/Preferences/Wipe）**与**跨 Repository/Runtime/Remote/Worker 的多步骤业务编排（Coordinator）。**Coordinator 统一归 P2 / `core:domain` 编排层**，不归 P5。 | `core:domain` 跨边界契约变更走 B0 capability request 审查；`core:data/*Repository` 签名变更走冻结层门禁。 |
| **P3 · LLM Generation Protocol** | `core:llm`（冻结） | `ChatGenerationRepository`、`BackgroundGenerationRepository`、Feature 生成状态展示 | `core:llm`（冻结） | 三阶段（Planner→Runtime→Result）、三 Provider 协议（`LlmCapabilities`/`LlmProfileValidator`/`LlmProfileCapabilityResolver`/`LlmGenerationPlanner`/`LlmGenerationRuntime`）、生成 outcome（`Generated`/`ProviderFailed`/`NoModelConfigured`/`UnsupportedVision`/`BlankPrompt`/`Stale`）。 | 冻结层门禁。P3-001、真实 LLM Provider transport、`core:llm` 改动即使排入 Wave 2B 也不自动获得修改授权，逐切片走门禁。 |
| **P4 · Online Remote Protocol** | `core:remote`（Track A 实现层，非冻结） | `core:domain` 同步编排、Feature 同步状态展示 | `core:remote` | Online API / Transport / Auth（`OnlineApi`/`OnlineHttpTransport`/`OnlineAuthSessionManager`/`AndroidOnlineAuthStateStore`）、网络错误与幂等语义。 | DTO/API 变更走 B0 capability request 审查；行为变更须按 P4 契约补测试。 |
| **P5 · Import / Export Protocol** | `core:protocol`（冻结） | `NativeImportRepository`、`NativeExportRepository`、Feature 导入导出 UI | `core:protocol`（冻结） + `core:data` 导入导出实现（冻结） | 版本化 schema（7 种）、`ProtocolExportPayloadBuilder`/`ProtocolExportPayloadValidator`/`ProtocolImportReader`、secret 脱敏策略、import 模式（Append/Overwrite/NewSpace）、overwrite 二次确认。**P5 是导入导出协议，不含 Coordinator。** | 冻结层门禁：schema 字段、脱敏策略、overwrite/new-space 语义变更须走门禁。 |
| **P6 · Persistence Contract** | `core:data/local`（冻结） + `core:data/preferences`（冻结） | `core:data/*Repository`（内部） | `core:data/local`、`core:data/preferences`（冻结） | Room schema（`DatabaseSchema.version`、18 张表、13 个 DAO、migrations）、exported schema JSON、DataStore key、`SecretStore`（alias/加密/SharedPreferences）。 | 冻结层门禁：Entity/DAO query/version/migration/schema JSON/SecretStore 策略变更须走门禁，含 migration test。 |
| **P7 · Design System Contract** | `core:design`（Track B） + `app/theme`（Track B） | Feature Compose Screen、可复用 UI 组件 | `core:design`、`app/theme`、`app/ui`（Track B） | Design Token、FormalIcons、主题（`ReverseTutorTheme`/`ReverseTutorTokens`）、可复用组件（`RtButton`/`RtCard`/`RtChip` 等）。 | Track B 自由重构，但不得为视觉改 domain enum/schema/protocol wire value。token/icon 语义变更须保证消费者兼容。 |

**硬性修正（必须执行）：**

- 禁止再出现「P5 Coordinator」。P5 是导入导出协议。
- Coordinator 统一归 **P2 / `core:domain` 编排层**。跨 Repository/Runtime/Remote/Worker 的多步骤业务编排属 P2，由 `core:domain` 的 Coordinator contracts 定义、由 Track A 实现并装配。
- 契约拓扑落仓（B2）时统一术语，禁止继续混用旧编号。

---

## 3. Repository、Coordinator、Facade 的职责裁决

三者不可互相替代，各自有明确职责边界：

**Repository**
- 提供单一数据能力的持久化/查询边界。
- 返回稳定的 domain model 或 result，不返回 UI 类型。
- 一个 Repository 对应一类数据（Session、Message、LlmProfile、ChatGeneration、BackgroundGeneration、Import、Export、Source、Memory、Graph、Preferences、Wipe）。
- 由 `DataModule` 统一装配；Repository 接口签名属冻结层（`core:data/*Repository`），变更走门禁。
- Repository 不做跨数据源的多步骤业务编排。

**Coordinator**
- 跨 Repository、Runtime、Remote 或 Worker 的多步骤业务编排。
- 负责生命周期、幂等、隔离和失败语义（如生成 token 校验、session 存在性校验、stale-token 拒绝、deleted-session 不写消息）。
- 归 P2 / `core:domain` 编排层；由 Track A 定义 contracts 并实现。
- Coordinator 不直接暴露给 UI 自行调用——UI 经 Facade 消费 Coordinator 的输出。

**Facade**
- 提供给 Feature/UI 的稳定能力入口。
- 将 Coordinator/Repository 输出映射为可消费的 UiState/UiEvent 输入输出。
- **禁止 UI 自行拼装多次 Repository 调用**——多步业务必须经 Coordinator，UI 只经 Facade 消费结果。
- Facade/ViewModel 归 Track A；UiState/UiEvent 由 Track A 定义，Track B 只消费。

**UiState / UiEvent**
- 由 Track A 定义（ViewModel/state/contracts 文件）。
- Track B 只消费不可变 UiState 和 UiEvent/Facade，不绕过 Facade/Coordinator 访问实现层（DAO/Entity/Database/SQL/SecretStore/协议 DTO）。

### 调用方向示例（3 个）

以下示例说明 UI → Facade/Coordinator → Repository/Runtime 的正确调用方向。精确签名以 Kotlin 源码为准，此处只描述方向与职责归属。

**示例 1：聊天生成**

```
ChatScreen (Track B, 消费 UiState)
  → ChatRunsViewModel / HomeViewModel (Track A, Facade/ViewModel)
    → ChatGenerationCoordinator 或等效编排 (Track A, P2 / core:domain)
      → MessageRepository.sendUserMessage (P2, 冻结)  // 规范化并持久化用户消息
      → ChatGenerationRepository.generateReply (P2, 冻结)  // 含 token/current-session 校验
        → LlmGenerationRuntime (P3, 冻结)  // 当前 FakeLlmGenerationRuntime
      → MessageRepository.saveMessage (P2, 冻结)  // 持久化 assistant 回复（仅 outcome=Generated）
    → 映射 ChatGenerationOutcome 到 UiState/UiEvent
  → ChatScreen 渲染生成状态/回复/错误
```

错误做法：ChatScreen 直接调 `MessageRepository` + `ChatGenerationRepository` 拼装生成流程，绕过 Coordinator 的 token 校验和 outcome 映射。

**示例 2：后台任务恢复/取消**

```
SessionSettingsScreen 或 SessionsScreen (Track B, 消费 UiState)
  → SessionHomeViewModel / WorkspaceViewModel (Track A, Facade/ViewModel)
    → BackgroundGenerationCoordinator 或等效编排 (Track A, P2 / core:domain)
      → BackgroundGenerationRepository.recoverInterruptedGenerationJobs (P2, 冻结)  // 恢复
      → BackgroundGenerationRepository.cancelSessionGenerationJobs (P2, 冻结)  // 取消
      → BackgroundGenerationRepository.runGenerationJob (P2, 冻结)  // 执行（持久化后才调 provider）
        → BackgroundGenerationWorker (Track A, Worker)  // WorkManager 持久化 job
    → 映射 BackgroundGenerationOutcome 到 UiState/UiEvent
  → UI 展示恢复/取消结果
```

错误做法：UI 直接调 `BackgroundGenerationRepository` 的 job 方法，绕过 Coordinator 的生命周期和隔离语义（结果写入前必须检查 session 仍存在且 token 仍当前）。

**示例 3：在线同步或导入导出**

```
FormalImportExportScreens / FormalSyncConflictScreens (Track B, 消费 UiState)
  → ModelConnectionsViewModel 或等效 (Track A, Facade/ViewModel)
    → SyncCoordinator / 导入导出编排 (Track A, P2 / core:domain)
      → NativeImportRepository.dryRun → importJson (P5, 冻结)  // dry run 后用户选模式
      → NativeExportRepository.currentSession / fullBackup / graphSnapshot (P5, 冻结)
      → OnlineRepositoryAdapters / core:remote (P4)  // 在线同步路径
    → 映射 import/export/sync 结果到 UiState/UiEvent
  → UI 展示 dry-run counts / warnings / errors，不止 toast
```

错误做法：导入导出 UI 直接调 `ProtocolImportReader`/`ProtocolExportPayloadBuilder` 或 Room 导入 store，绕过 Repository 的 overwrite 二次确认和 secret 脱敏。

---

## 4. Track A / Track B 边界裁决

引用 A4 物理文件所有权规则（`tasks/wave0-a4-ownership-table.md`），不重复整张表。

**Track A · 领域能力与状态流：**
- `core:domain`、`core:data`、`core:llm`、`core:remote`（后三者冻结层需门禁）。
- Coordinator、Repository 实现、Worker（`BackgroundGenerationWorker`）、迁移、Provider runtime、装配逻辑（`app/wiring`、`DataModule`、各 `*Module`）。
- Feature 的 state、event、facade、ViewModel、contracts、数据模型（UiState/UiEvent 定义者）。

**Track B · 表现层与交互：**
- Compose Screen、可复用 UI 组件（`app/ui`）、theme（`app/theme`）、icons、纯导航 Route。
- `core:design`（Design Token / FormalIcons）。
- 只消费不可变 UiState 和 UiEvent/Facade。

**hotspot 规则（引用 A4 §4）：**
- 同一文件内既有 Track A 职责（state/event/facade/coordinator/runtime/装配）又有 Track B 职责（Composable Screen/Route）→ 标为 hotspot，指定单一临时 owner（当前 3 个 hotspot 临时 owner 为 Track A）。
- **两轨不得同时编辑同一物理文件**（硬约束，靠物理文件所有权表 + 分支/合并规则保证）。
- 需跨轨改动先拆分（提取 state/Composable 到独立文件）再各自认领；拆分前 Track B 不得直接改 hotspot 文件。

**Track B 禁止直接接触（引用 A4 + AGENTS.md §0.1 + freeze §2.1）：**
- Track B 不得直接 import 或调用 DAO、Entity、`ReverseTutorDatabase`、`DatabaseSchema`、SQL、schema JSON、`SecretStore`/`AndroidKeystoreSecretStore`、`RoomNativeImportStore`/`RoomNativeExportStore`、协议 DTO（`ProtocolImportReader`/`ProtocolExportPayloadBuilder` 等）。
- 越界由 B4 护栏（Gradle 依赖白名单 + 静态导入检查）强制为 0。

**`core:domain` 与 `core:remote` 的冻结状态澄清（引用 A4 §0）：**
- `core:domain` 与 `core:remote` 归 Track A，但**不在 AGENTS.md §0.1 的冻结清单内**——不能错误声明为已冻结层。
- 跨边界契约变更（Coordinator contracts、OnlineApi/Transport DTO）仍要经 B0 capability request 审查；行为变更须补契约测试。
- 冻结层仅限 AGENTS.md §0.1 + A3 清单：`core:model`、`core:protocol`、`core:llm`、`core:data/*Repository`、`core:data/local`、`core:data/preferences`、`core:data/llm/SecretStore.kt`、Room schema exports/migrations。

---

## 5. 冻结层变更裁决

引用 A3（`tasks/wave0-a3-freeze-boundary.md`）与 AGENTS.md §0.1，不复制 8 段模板全文。

以下改动必须先有变更说明（A3 §3 模板）、测试方案和用户明确批准，不得混在 UI/feature 分支中：

| 冻结模块 | 路径 | 变更门禁触发条件 |
|---|---|---|
| `core:model` | `core/model/...` | 新增/删除/修改枚举值或 domain 类型签名 |
| `core:protocol` | `core/protocol/...` | 修改 schema required/allowed fields、secret 脱敏策略、`ProtocolImportReader`/`ProtocolExportPayloadBuilder`/`Validator` 行为 |
| `core:llm` | `core/llm/...` | 修改三阶段/三 Provider 协议、`LlmCapabilities`/`LlmProfileValidator`/`LlmGenerationRuntime` |
| `core:data/*Repository` | `core/data/.../各 Repository` | 修改任何 Repository 方法签名或输入/输出类型 |
| `core:data/local` | `core/data/local/...` | 修改 Room Entity 字段/表名/索引、DAO query、`DatabaseSchema.version`、migrations、exported schema JSON |
| `core:data/preferences` | `core/data/preferences/...` | 修改 DataStore key、`AppPreferencesRepository` 实现、新增/复用 key 语义 |
| `SecretStore` | `core/data/llm/SecretStore.kt` | 修改存储位置、alias、加密策略、SharedPreferences 名 |

**尤其注明（引用 A3 §1.4 + audit v2.1 不可逆约束）：**

- **P3-001**（后台可靠性 / WorkManager 持久化 job 补差距）即使排入 Wave 2B，也不自动获得修改授权——逐切片走门禁。
- **真实 LLM Provider transport 切换**即使排入 Wave 2B，也不自动获得修改授权。
- **Room migration**（如有新 schema 版本）即使排入 Wave 2，也不自动获得修改授权。
- **`core:llm` 模块改动**即使排入 Wave 2B，也不自动获得修改授权。

审批流程（引用 A3 §4）：切片开始前 → 填写变更说明（A3 §3 模板）→ 提交用户审批 → 批准后执行变更 → 补测试 → 验证全绿 → 归档至 `tasks/change-requests/cr-YYYYMMDD-简述.md`。未获批准前不得修改冻结层代码；已获批准的变更须在 PR/commit 中引用变更说明文件名。

---

## 6. Capability Request 流程

当 Track B（或任一轨道）发现业务能力缺口、现有 Facade/Coordinator/Repository 不足以支撑 UI 需求时，走以下标准流程。

### 6.1 最小字段

每个 capability request 至少包含：

| 字段 | 说明 |
|---|---|
| 能力缺口 ID | `cap-YYYYMMDD-简述` |
| 提出轨道与切片 | Track A / Track B + 对应 Wave/切片（如 Wave 2C / chat-composer） |
| 用户可见目标 | 缺口对应的用户可见功能目标 |
| 当前 Facade/Coordinator/Repository 为什么不足 | 指出现有能力的具体不足（缺方法/缺字段/缺编排/缺 outcome 映射） |
| 涉及 P1–P7 哪些边界 | 列出受影响协议（P1–P7） |
| 是否触及冻结层 | 是/否；若是，列出具体冻结模块（引用 A3） |
| 候选方案与兼容策略 | 至少一个候选方案 + 向后兼容分析 |
| 必须新增/修改的测试 | 列出测试文件/类/覆盖点 |
| 需要用户批准的具体事项 | 明确列出需用户拍板的点（冻结层变更、enum 新增、schema 变更等） |

### 6.2 流程

```
Track B 发现缺口
  → 提交 capability request（按 6.1 最小字段，归档至 tasks/capability-requests/）
    → Track A 判断是否可由现有 Facade/adapter 解决
      ├→ 可由现有能力解决 → Track A 补 Facade/adapter + 契约测试 → Track B 接入
      └→ 需底层变更
           → 若触及冻结层：写冻结变更说明（A3 §3 模板）+ 测试方案 → 用户批准
           → 若不触及冻结层（如 core:domain/core:remote 契约）：写跨边界变更说明 + 契约测试 → B0 审查
             → 最小实现
               → 契约测试 + 模块测试 + 设备 smoke
                 → 更新 Route↔LEG 映射（B3）
```

**规则：**
- Track B 不得在自身分支内直接新增领域方法或改冻结层——必须走 capability request 由 Track A 先补契约与实现。
- 触及冻结层的 capability request 在用户批准前不得实现。
- capability request 归档路径：`tasks/capability-requests/cap-YYYYMMDD-简述.md`。
- 实现完成后更新 B3 的 Route↔LEG 映射，记录能力变更对验收证据的影响。

---

## 7. B0 完成验收

逐项自检：

- [x] P1–P7 术语完整（7 条全部定义，含定义者/消费者/实现者/职责/变更门禁），且没有「P5 Coordinator」——Coordinator 统一归 P2 / `core:domain` 编排层。
- [x] Repository / Coordinator / Facade 职责和调用方向明确（三者不可替代 + 3 个实际例子：聊天生成、后台任务恢复/取消、在线同步或导入导出）。
- [x] Track A/B 边界与 A4 物理文件所有权一致（引用 A4，不重复整表；hotspot 规则引用 A4 §4；Track B 禁止直接接触清单引用 A4 + AGENTS.md §0.1 + freeze §2.1；`core:domain`/`core:remote` 非冻结但走 B0 审查）。
- [x] 冻结范围与 A3、AGENTS.md §0.1 一致（引用 A3，不复制 8 段模板全文；P3-001/真实 LLM/Room migration/core:llm 注明不自动获授权）。
- [x] capability request 可直接执行（最小字段 9 项 + 完整流程图 + 归档路径）。
- [x] 未改业务代码、冻结层、Gradle、测试、签名、PWA/Capacitor（仅新增本 markdown 文档）。
- [x] 未执行 git push、tag、commit（留在工作树等审查）。
- [x] 未开始 B1（本文件完成后停止，不进入 B1 代码事实盘点）。
