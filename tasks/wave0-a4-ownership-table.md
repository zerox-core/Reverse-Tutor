# Wave 0 · A4 物理文件所有权表

> 生成时间：2026-08-14（周五）
> 执行人：本地开发搭档（feishu_mcp）
> 总纲：v2.1 审计结论与落地计划（`tasks/audit-conclusion-and-plan.md`）
> 依据：freeze 文档 + 实地递归扫描 `mobile-native/` 全部 `src/main` 源码（HEAD `9625298`，工作树无业务代码改动）
> 验收标准：每个源码文件有唯一责任人；混合文件标 hotspot 并指定单一临时 owner；两条轨道不同时改同一文件。

## 0. 所有权规则

- **Track A（领域能力与状态流）**：core 模块（除 design）、Coordinator、Repository 实现、Worker、wiring/adapter、ViewModel、state/contracts/models
- **Track B（表现层与交互）**：Compose Screen、可复用 UI 组件、theme、icons、纯导航 Route、core:design
- **冻结层**：`core:model`、`core:protocol`、`core:llm`、`core:data/*Repository` 与 `core:data` 的 local/preferences/SecretStore 范围——需变更审批门禁（详见 A3）。`core:domain` 与 `core:remote` 归 Track A，但不在 AGENTS.md §0.1 的冻结清单内；跨边界契约变更仍需走 B0 capability request 审查。
- **hotspot**：同一文件内既有 Track A 职责（state/event/facade/coordinator/runtime/装配）又有 Track B 职责（Composable Screen/Route）→ 指定单一临时 owner，另一轨不得直接改；需跨轨改动先拆分
- **共享接口**：Track A 与 Track B 之间的对接面（UiState / UiEvent / Facade / Coordinator 输入输出 / Port 接口）
- **集成负责人**：每条垂直切片的集成归属（默认 Track A，因为装配与状态流是集成主轴）

> 扫描口径：仅统计 `src/main/java` 下 `.kt` 文件。test/androidTest 不计入所有权表（测试归属各模块负责人）。app+feature+core main 合计 **207 个 .kt 文件**。

## 1. core 模块（逐模块）

冻结范围按 AGENTS.md §0.1 与 A3 执行；`core:domain`、`core:remote` 为 Track A 管理的契约/实现层，变更须经过 B0 的跨边界审查，但不能错误标记为已冻结。下表按模块列出路径前缀、文件数、共享接口。

| 模块 | 路径前缀 | 文件数 | 唯一责任人 | 可修改类型 | 共享接口 | 集成负责人 |
|---|---|---:|---|---|---|---|
| `core:model` | `core/model/src/main/java/com/reversetutor/core/model/` | 14 | 冻结（Track A） | 仅经审批门禁 | domain model 类型 | Track A |
| `core:protocol` | `core/protocol/src/main/java/com/reversetutor/core/protocol/` | 6 | 冻结（Track A） | 仅经审批门禁 | protocol facade / Schema | Track A |
| `core:domain` | `core/domain/src/main/java/com/reversetutor/core/domain/` | 6 | Track A（契约层） | B0 capability request / 跨边界审查 | Coordinator contracts | Track A |
| `core:data` | `core/data/src/main/java/com/reversetutor/core/data/` | 34 | 冻结（Track A） | 仅经审批门禁 | Repository 接口 / DAO / Entity | Track A |
| `core:llm` | `core/llm/src/main/java/com/reversetutor/core/llm/` | 7 | 冻结（Track A） | 仅经审批门禁 | LlmCapabilities / Runtime | Track A |
| `core:remote` | `core/remote/src/main/java/com/reversetutor/core/remote/` | 11 | Track A（实现层） | 按 P4 契约与测试变更；DTO/API 变更走 B0 审查 | OnlineApi / Transport | Track A |
| `core:design` | `core/design/src/main/java/com/reversetutor/core/design/` | 2 | **Track B** | 可自由重构 | DesignToken / FormalIcons | Track B |

core 各模块文件清单（用于物理文件所有权；冻结状态以表格和 A3 为准）：

- **core:model (14)**：ConversationModels、ConversationRunModels、DomainError、GraphModels、InteractionModels、LearningModels、LlmProfile、MemoryModels、ModelConnectionModels、OperationalModels、SourceModels、Space、SyncModels、WorldTreeModels
- **core:protocol (6)**：LightweightJson、NativeSessionPreset、ProtocolImportReader、ProtocolModule、VersionedProtocolSchemas、`export/ProtocolExportPayloads`
- **core:domain (6)**：ConversationRunCoordinator、ModelConnectionCoordinator、OnlineContentContracts、OnlineInsightContracts、RepositoryContracts、SyncCoordinator
- **core:data (34)**：DataModule；`background/BackgroundGenerationRepository`；`graph/GraphRepository`；`learning/LearningRepositoryImpl`、`learning/WidgetLayoutRepositoryImpl`；`llm/ChatGenerationRepository`、`llm/LlmProfileRepository`、`llm/SecretStore`；`local/DatabaseSchema`、`local/ReverseTutorDatabase`、`local/dao/Daos`、`local/dao/HybridDaos`、`local/dao/WorldTreeDao`、`local/entity/Entities`、`local/entity/HybridEntities`、`local/entity/WorldTreeEntities`；`memory/MemoryRepository`；`message/MessageRepository`；`migration/NativeExportRepository`、`migration/NativeImportRepository`；`model/ModelConnectionRepositoryImpl`；`online/ContractMockOnlineApi`、`online/OnlineRepositoryAdapters`；`preferences/AppPreferenceKeys`、`preferences/AppPreferences`、`preferences/AppPreferencesRepository`；`run/ConversationRunRepositoryImpl`；`search/RoomGlobalSearchRepository`；`session/SessionDeletionRepository`、`session/SessionRepository`；`sources/SourceRepository`；`sync/RoomSyncRepository`；`wipe/LocalDataWipeRepository`；`worldtree/LocalDataWipeRepository`、`worldtree/WorldTreePayloadCodec`
  - 注：`wipe/LocalDataWipeRepository.kt` 与 `worldtree/LocalDataWipeRepository.kt` 同名不同包，均为冻结文件。
- **core:llm (7)**：LlmGenerationLifecycle、LlmModule、LlmProfilePolicy、ProductionLlmGenerationRuntime、ProviderHttpTransport、ProviderRuntimeFoundation、UrlConnectionProviderHttpTransport
- **core:remote (11)**：AndroidOnlineAuthStateStore、AuthApi、HttpOnlineApi、OnlineActivityModels、OnlineApi、OnlineAuthModels、OnlineAuthSessionManager、OnlineCapabilities、OnlineContentModels、OnlineHttpTransport、UrlConnectionOnlineHttpTransport
- **core:design (2, Track B)**：FormalDesign、FormalIcons

## 2. app 模块文件所有权（64 文件）

### 2.1 app 根（1 文件）

| 文件 | 责任人 | 可修改类型 | 共享接口 | 集成负责人 |
|---|---|---|---|---|
| `MainActivity.kt` | Track A | 装配/入口 | — | Track A |

### 2.2 app/shell/（32 文件）

| 文件 | 责任人 | 可修改类型 | 共享接口 | 集成负责人 |
|---|---|---|---|---|
| `ActivityAnnouncementDialog.kt` | Track B | Composable Dialog | UiState（消费） | Track A |
| `AppNavigation.kt` | Track B | 纯导航表现 | Route 定义 | Track A |
| `AppShell.kt` | **hotspot → Track A（临时）** | 装配+Composable | UiState / 导航 | Track A |
| `ChallengeDetailUiState.kt` | Track A | state | UiState 定义 | Track A |
| `ChallengeJoinFlowCoordinator.kt` | Track A | Coordinator | Coordinator IO | Track A |
| `ChallengePresentation.kt` | Track B | Composable | UiState（消费） | Track A |
| `ChallengeRoute.kt` | Track B | 纯导航 Route | — | Track A |
| `ChallengeRuntimeCoordinator.kt` | Track A | Coordinator | Coordinator IO | Track A |
| `ChallengeSessionLaunch.kt` | Track A | 运行时编排 | — | Track A |
| `ChallengeVerticalStateMachine.kt` | Track A | StateMachine | — | Track A |
| `ChatAttachmentPlatformAdapters.kt` | Track A | 平台 adapter | — | Track A |
| `ChatMessagePlatformAdapters.kt` | Track A | 平台 adapter | — | Track A |
| `CommunityRoute.kt` | Track B | 纯导航 Route | — | Track A |
| `FigmaAppUiState.kt` | Track A | state | UiState 定义 | Track A |
| `FormalBatch6RuntimeRoutes.kt` | **hotspot → Track A（临时）** | Runtime+Route | Route 定义 | Track A |
| `FormalSearchArticleRuntimeRoutes.kt` | **hotspot → Track A（临时）** | Runtime+Route | Route 定义 | Track A |
| `FormalSessionSettingsScreens.kt` | Track B | Composable Screen | UiState（消费） | Track A |
| `GraphEdgePagingOverlay.kt` | Track B | Composable Overlay | UiState（消费） | Track A |
| `HomeChallengePagerHost.kt` | Track B | Composable PagerHost | UiState（消费） | Track A |
| `RepositoryChatReferenceQueryPort.kt` | Track A | Repository Port | Port 接口 | Track A |
| `SessionLibrarySettingsScreen.kt` | Track B | Composable Screen | UiState（消费） | Track A |
| `SessionLibrarySettingsState.kt` | Track A | state | UiState 定义 | Track A |
| `SessionSettingsProductionAdapters.kt` | Track A | 生产 adapter | — | Track A |
| `SessionSettingsRoute.kt` | Track B | 纯导航 Route | — | Track A |
| `SessionWorldTreeRoute.kt` | Track B | 纯导航 Route | — | Track A |
| `SourceImportInputFactory.kt` | Track A | Factory/wiring | — | Track A |
| `WorkspaceBackHandler.kt` | Track B | UI 行为 | — | Track A |
| `WorkspacePageIndicator.kt` | Track B | UI 组件 | — | Track A |
| `WorkspacePagerHost.kt` | Track B | Composable PagerHost | UiState（消费） | Track A |
| `WorkspaceSurfaceState.kt` | Track A | state | UiState 定义 | Track A |
| `WorkspaceUiState.kt` | Track A | state | UiState 定义 | Track A |
| `WorkspaceViewModel.kt` | Track A | ViewModel | UiState / UiEvent | Track A |

### 2.3 app/ui/（14 文件，全部 Track B）

DesignSystemPreview、ReverseTutorActions、ReverseTutorDialogs、ReverseTutorScaffold、ReverseTutorStatus、ReverseTutorSurfaces、RtButton、RtCard、RtChip、RtControls、RtEmptyState、RtIcons、RtProgress、RtSectionTitle —— 可修改类型均为「UI 组件」，集成负责人 Track B。

### 2.4 app/theme/（2 文件，全部 Track B）

`ReverseTutorTheme.kt`（主题）、`ReverseTutorTokens.kt`（Design Token）—— 集成负责人 Track B。

### 2.5 app/wiring/（14 文件，全部 Track A）

DurableSessionDeletionCoordinator、HybridAppGraph、HybridFrontendPortAdapters、RepositoryNewSessionCreatePortAdapter、SharedPreferencesChatAttachmentOrderStore、SharedPreferencesChatDraftStore、SharedPreferencesChatPendingDeletionStore、SharedPreferencesChatRememberedMessageStore、SharedPreferencesChatSourceUsagePort、SharedPreferencesNewSessionPersistence、SharedPreferencesSessionHomePersistence、SharedPreferencesSessionSettingsStore、SharedPreferencesTagLibraryPersistence、SharedPreferencesWeeklyDashboardConfigurationStore —— 可修改类型「装配/持久化 adapter/Port adapter」，集成负责人 Track A。

### 2.6 app/background/（1 文件，Track A）

`BackgroundGenerationWorker.kt` —— Worker，共享接口 BackgroundGenerationRepository，集成负责人 Track A。

## 3. feature 模块文件所有权（63 文件）

### 3.1 feature/chat/（32 文件）

| 文件 | 责任人 | 可修改类型 | 共享接口 | 集成负责人 |
|---|---|---|---|---|
| `ChallengeSessionProvenance.kt` | Track A | 数据模型 | — | Track A |
| `ChatComposerContracts.kt` | Track A | contracts | UiEvent 定义 | Track A |
| `ChatContextEvidence.kt` | Track A | 数据模型 | — | Track A |
| `ChatImageLoader.kt` | Track B | UI 工具 | — | Track B |
| `ChatMessageActionContracts.kt` | Track A | contracts | UiEvent 定义 | Track A |
| `ChatModule.kt` | Track A | DI | — | Track A |
| `ChatReferenceQueryContracts.kt` | Track A | contracts | — | Track A |
| `ChatReferenceQueryScreen.kt` | Track B | Composable Screen | UiState（消费） | Track A |
| `ChatRunsViewModel.kt` | Track A | ViewModel | UiState / UiEvent | Track A |
| `ChatScreen.kt` | Track B | Composable Screen | UiState（消费） | Track A |
| `ChatUiState.kt` | Track A | state | UiState 定义 | Track A |
| `CustomColumnEditorScreen.kt` | Track B | Composable Screen | UiState（消费） | Track A |
| `CustomColumnModels.kt` | Track A | 数据模型 | UiState 定义 | Track A |
| `DragDropBounds.kt` | Track B | UI 布局 | — | Track B |
| `FigmaNewSessionScreen.kt` | Track B | Composable Screen | UiState（消费） | Track A |
| `FormalDraftEditorScreen.kt` | Track B | Composable Screen | UiState（消费） | Track A |
| `FormalHomeScreen.kt` | Track B | Composable Screen | UiState（消费） | Track A |
| `FormalPresetModels.kt` | Track A | 数据模型 | UiState 定义 | Track A |
| `HomeViewModel.kt` | Track A | ViewModel | UiState / UiEvent | Track A |
| `LearnerAvatarReference.kt` | Track B | UI 引用 | — | Track B |
| `NewSessionLifecycle.kt` | Track A | 生命周期 | — | Track A |
| `NewSessionModels.kt` | Track A | 数据模型 | UiState 定义 | Track A |
| `NewSessionPrefill.kt` | Track A | 预填充逻辑 | — | Track A |
| `ReverseTeachingChatScreen.kt` | Track B | Composable Screen | UiState（消费） | Track A |
| `SessionHomePersistence.kt` | Track A | 持久化 | — | Track A |
| `SessionHomeViewModel.kt` | Track A | ViewModel | UiState / UiEvent | Track A |
| `SessionListModels.kt` | Track A | 数据模型 | UiState 定义 | Track A |
| `SessionSettingsContracts.kt` | Track A | contracts | UiEvent 定义 | Track A |
| `SessionSettingsScreen.kt` | Track B | Composable Screen | UiState（消费） | Track A |
| `SessionsScreen.kt` | Track B | Composable Screen | UiState（消费） | Track A |
| `TagLibraryModels.kt` | Track A | 数据模型 | UiState 定义 | Track A |
| `Task2B1NewSessionScreen.kt` | Track B | Composable Screen | UiState（消费） | Track A |

### 3.2 feature/memory/（13 文件）

| 文件 | 责任人 | 可修改类型 | 共享接口 | 集成负责人 |
|---|---|---|---|---|
| `BrainScreen.kt` | Track B | Composable Screen | UiState（消费） | Track A |
| `ContextHubModels.kt` | Track A | 数据模型 | UiState 定义 | Track A |
| `ContextHubScreen.kt` | Track B | Composable Screen | UiState（消费） | Track A |
| `FormalKnowledgeGraphScreens.kt` | Track B | Composable Screen | UiState（消费） | Track A |
| `FormalWeeklyModels.kt` | Track A | 数据模型 | UiState 定义 | Track A |
| `FormalWeeklyScreen.kt` | Track B | Composable Screen | UiState（消费） | Track A |
| `GraphInteractionPolicy.kt` | Track A | 策略逻辑 | — | Track A |
| `KnowledgeGraphModels.kt` | Track A | 数据模型 | UiState 定义 | Track A |
| `KnowledgeGraphPanel.kt` | Track B | Composable Panel | UiState（消费） | Track A |
| `MemoryModule.kt` | Track A | DI | — | Track A |
| `WeeklyDashboardContracts.kt` | Track A | contracts | UiEvent 定义 | Track A |
| `WeeklyDashboardGridScreen.kt` | Track B | Composable Screen | UiState（消费） | Track A |
| `WeeklyDashboardViewModel.kt` | Track A | ViewModel | UiState / UiEvent | Track A |

### 3.3 feature/settings/（15 文件）

| 文件 | 责任人 | 可修改类型 | 共享接口 | 集成负责人 |
|---|---|---|---|---|
| `FigmaSettingsScreen.kt` | Track B | Composable Screen | UiState（消费） | Track A |
| `FormalBatch6Components.kt` | Track B | UI 组件 | — | Track B |
| `FormalBatch6PreviewFixtures.kt` | Track B | Preview fixture | — | Track B |
| `FormalDiagnosticsScreens.kt` | Track B | Composable Screen | UiState（消费） | Track A |
| `FormalImportExportScreens.kt` | Track B | Composable Screen | UiState（消费） | Track A |
| `FormalLlmConfigurationScreen.kt` | Track B | Composable Screen | UiState（消费） | Track A |
| `FormalSearchAndArticleScreens.kt` | Track B | Composable Screen | UiState（消费） | Track A |
| `FormalSettingsScreen.kt` | Track B | Composable Screen | UiState（消费） | Track A |
| `FormalSyncConflictScreens.kt` | Track B | Composable Screen | UiState（消费） | Track A |
| `FormalTokenScreens.kt` | Track B | Composable Screen | UiState（消费） | Track A |
| `FormalUpdateScreen.kt` | Track B | Composable Screen | UiState（消费） | Track A |
| `ModelConnectionsViewModel.kt` | Track A | ViewModel | UiState / UiEvent | Track A |
| `SettingsFoundationModels.kt` | Track A | 数据模型 | UiState 定义 | Track A |
| `SettingsFoundationScreen.kt` | Track B | Composable Screen | UiState（消费） | Track A |
| `SettingsModule.kt` | Track A | DI | — | Track A |

### 3.4 feature/sources/（3 文件）

| 文件 | 责任人 | 可修改类型 | 共享接口 | 集成负责人 |
|---|---|---|---|---|
| `SourcesModels.kt` | Track A | 数据模型 | UiState 定义 | Track A |
| `SourcesModule.kt` | Track A | DI | — | Track A |
| `SourcesScreen.kt` | Track B | Composable Screen | UiState（消费） | Track A |

## 4. hotspot 文件清单（3 个）

以下文件在命名上同时包含 Track A 职责（Runtime/Coordinator/装配）和 Track B 职责（Route/Screen），需在 Wave 1 B1 代码事实盘点时逐文件检查内容，确认是否真正混合。在确认前，指定 Track A 为单一临时 owner（Runtime/Coordinator 逻辑更难拆分）。

| 文件 | 命名特征 | 临时 owner | 拆分建议 |
|---|---|---|---|
| `shell/AppShell.kt` | 装配+Composable | Track A | 提取 Composable shell 到 `AppShellScreen.kt`（Track B），保留装配在 `AppShell.kt`（Track A） |
| `shell/FormalBatch6RuntimeRoutes.kt` | Runtime+Route | Track A | 提取 Route 定义到独立文件（Track B），保留 Runtime 逻辑（Track A） |
| `shell/FormalSearchArticleRuntimeRoutes.kt` | Runtime+Route | Track A | 同上 |

> **规则**：hotspot 文件在拆分完成前，Track B 不得直接修改。如需跨轨改动，先走拆分（提取 state/Composable 到独立文件）再各自认领。

## 5. 分支/合并规则

- **分支命名**：`track-a/<切片名>` 或 `track-b/<切片名>`
- **合并冲突**：hotspot 文件的冲突必须由 Track A 负责人解决（临时 owner 是 Track A）
- **合并门禁**：每个 PR 必须声明涉及的文件列表和对应 Track，B4 护栏检查通过后方可合并
- **跨轨依赖**：Track B 需要 Track A 的新能力时，提交 capability request；Track A 先补契约测试和变更说明，再按冻结流程申请修改
- **能力缺口**：Track B 缺少所需 Repository/Facade 方法 → 走 capability request，由 Track A 先补契约与实现，Track B 再接入；不得在 Track B 分支内直接新增领域方法

## 6. 统计摘要（实地扫描，HEAD 9625298）

| 分类 | 文件数 | 占比 |
|---|---:|---:|
| app 模块 | 64 | 30.9% |
| feature 模块 | 63 | 30.4% |
| core 冻结层（model/protocol/domain/data/llm/remote） | 78 | 37.7% |
| core:design（Track B） | 2 | 1.0% |
| **main .kt 合计** | **207** | 100% |

按责任人归并（app+feature+core:design，共 129 个非冻结文件；core 冻结 78 个单独计）：

| 责任人 | 文件数（非冻结） | 说明 |
|---|---:|---|
| Track A | 73 | state/ViewModel/Coordinator/wiring/adapter/DI/contracts/models |
| Track B | 53 | Compose Screen/UI 组件/theme/icons/Route |
| hotspot（临时 Track A） | 3 | AppShell / FormalBatch6RuntimeRoutes / FormalSearchArticleRuntimeRoutes |
| 冻结层（Track A，需审批） | 78 | core 六模块 |

## 7. A4 完成自检

- [x] 每个 main 源码文件有唯一责任人（Track A / Track B / hotspot 临时 owner / 冻结）。
- [x] 混合文件标为 hotspot 并指定单一临时 owner（3 个）。
- [x] 两条轨道不同时改同一文件（硬约束，靠分支/合并规则保证）。
- [x] 冻结层文件标注需审批门禁（交叉引用 A3），并逐模块列出文件清单。
- [x] 共享接口列标注了 UiState / UiEvent / Facade / Coordinator IO / Port 接口。
- [x] 集成负责人列标注了每条切片的集成归属。
- [x] app/shell 按物理文件职责划分（MainActivity 在 app 根，非 shell）；app/wiring 归 Track A。
- [x] 文件数以实地递归扫描为准（207 main .kt），非旧报告数字。

## 8. 遗留与下一步

- hotspot 文件的具体内容需在 Wave 1 B1 代码事实盘点时逐文件确认，当前分类基于文件命名约定。
- 部分文件可能存在命名与实际内容不一致的情况（如 `*Screen.kt` 内含 state 定义），B1 时修正。
- core:data 中 `wipe/LocalDataWipeRepository.kt` 与 `worldtree/LocalDataWipeRepository.kt` 同名不同包，B1 时确认两者职责是否重复。
- 下一项：**A5 worktree 盘点**。
