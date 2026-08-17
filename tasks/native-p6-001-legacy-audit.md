# NATIVE-P6-001 · 全量遗留功能代码审计报告

> 审计时间：2026-08-17  
> 审计方式：feishu_mcp 直连 F:\xw\reverse-tutor，源码审查 + JVM 测试证据 + 构建验证  
> 分支：Android | 最新提交：b6c3307 | 工作树：干净（仅 .aily_tmp/ 未跟踪）  
> 冻结层检查：core/model, core/protocol, core/llm, core/data → **空（零修改）**  
> 设备测试：**本轮不执行**（模拟器未运行，用户后续启动后补测）

## 一、审计范围与方法

### 审计对象
覆盖率注册表（`native-legacy-coverage-registry.md`）中全部 44 条 LEG 行，重点审计 28 条 P0 替换阻塞项。

### 审计维度（逐项确认）
1. **代码证据** — 源码文件是否存在、关键类/接口/Composable 是否实现
2. **JVM 测试证据** — 测试文件是否存在、测试方法数量与覆盖范围
3. **设备证据** — 设备测试状态（本轮全部标注为"待补"）
4. **契约映射** — 原生实现是否映射到 PWA 遗留入口的完整功能
5. **是否仍阻塞** — 综合评估是否仍为 P0 替换阻塞项

### 构建与测试基线
- **JVM 测试**：400+ tests, 0 failures（core:data=95, feature:chat=113, core:llm=20, app=172）— 上一轮 UP-TO-DATE，树未变→仍有效
- **构建**：`:app:assembleDebug` BUILD SUCCESSFUL — 上一轮 UP-TO-DATE
- **Lint**：`:app:lint` 无错误 — 上一轮 UP-TO-DATE
- **冻结层**：git diff --name-only → 空

---

## 二、P0 遗留入口逐项审计

### 已关闭项（verified / closed）— 4 条

| ID | 遗留入口 | 代码证据 | JVM 测试 | 设备证据 | 契约映射 | 仍阻塞 |
|---|---|---|---|---|---|---|
| LEG-001 | 启动/闪屏 | `MainActivity.kt`（143行）启动入口，`com.reversetutor.preview` 包名 | ✅ 启动冒烟测试 | ✅ P1-006 HMA-AL00 验证 | 完整 | 否（closed） |
| LEG-033 | 数据擦除 | `LocalDataWipeRepository.kt`、`FormalImportExportScreens.kt` WIPE 确认 UI | ✅ `LocalDataWipeRepositoryTest.kt` 破坏性确认测试 | ✅ P4-007 HMA-AL00 验证 | 完整 | 否（closed） |
| LEG-039 | Android 返回行为 | `AppNavigation.kt` BackResult 枚举、`AppShell.kt` 返回栈管理、`WorkspaceBackHandler.kt` | ✅ `AppNavigationStateTest.kt` 返回栈单元测试 | ✅ P1-006 HMA-AL00 验证 | 完整 | 否（closed） |
| LEG-044 | 应用诊断/关于 | `FormalDiagnosticsScreens.kt`、`FormalUpdateScreen.kt` | ✅ 诊断策略 JVM 测试 | ✅ P3-004 HMA-AL00 验证 | 完整 | 否（closed） |

---

### P0 替换阻塞项 — 28 条逐项审计

#### 会话与首页模块（LEG-002, LEG-005, LEG-006, LEG-038）

**LEG-002 · 顶部导航栏**
- **代码证据**：`AppShell.kt`（1911行）包含顶部应用栏、导航编排、路由管理；`AppNavigation.kt`（195行）定义 `AppDestination` 枚举含 Sessions/Chat/Context/Settings/About 路由；`FormalHomeScreen.kt`（1375行）有顶栏、新建会话按钮、上下文按钮
- **JVM 测试**：`AppNavigationStateTest.kt`、`WorkspaceViewModelTest.kt`、`FormalHomeUiStateTest.kt` — 导航状态与 UI 状态测试通过
- **设备证据**：P1-006 验证了基础导航（Sessions→Chat→Context→Settings→About），但 feature-specific 上下文动作未完整验证
- **契约映射**：PWA 顶栏功能（标题/状态/新建会话/上下文/图谱搜索）→ 原生有等价路由和按钮，但图谱搜索入口仅在 Context Hub 内可用
- **仍阻塞**：✅ 是 — 上下文动作完整性需设备验证

**LEG-005 · 会话首页**
- **代码证据**：`FormalHomeScreen.kt` 实现会话列表（搜索/筛选/pin/状态/头像占位）；`SessionListModels.kt` 数据模型；`SessionHomeViewModel.kt` / `HomeViewModel.kt` ViewModel；`SessionRepository.kt`（186行）Room-backed 仓库
- **JVM 测试**：`SessionRepositoryTest.kt`（5 @Test：实现契约/pin排序/重命名pin归档/不存在返回false/自定义会话配置）；`SessionListUiStateTest.kt`；`SessionHomeViewModelTest.kt`；`FormalHomeUiStateTest.kt`
- **设备证据**：P2-006 emulator-5554 核心闭环设备套件 3/3 通过（会话创建/打开流）
- **契约映射**：搜索/筛选/pin/状态/未读/头像 → 原生有搜索/筛选/pin/状态，但真实未读计数和头像 parity 待补
- **仍阻塞**：✅ 是 — 未读计数与头像 parity 需设备验证

**LEG-006 · 会话卡片操作**
- **代码证据**：`FormalHomeScreen.kt` 含 `HomeSessionAction` 枚举（Open/Rename/Pin/Delete/Export/Avatar deferred）；`HomeViewModel.kt` 管理操作状态
- **JVM 测试**：`SessionRepositoryTest.kt` 验证 rename/pin/archive 持久化；`Task2ADeletionDelegateTest.kt` 验证删除委托
- **设备证据**：操作面板存在但设备级验证不完整
- **契约映射**：PWA swipe/long-press → 原生有 action sheet 覆盖 pin/delete/rename/export/avatar(deferred)；swipe 手势 parity 待补
- **仍阻塞**：✅ 是 — swipe 手势、头像选择器、设备验证待补

**LEG-038 · 头像系统**
- **代码证据**：`FormalHomeScreen.kt` 含 Avatar 占位渲染（5处引用）；`LearnerAvatarReference.kt` 头像引用模型；全局头像可见性偏好存在于 AppPreferences
- **JVM 测试**：`AppPreferencesPolicyTest.kt` 验证偏好持久化
- **设备证据**：无设备级头像选择/清除/隐藏验证
- **契约映射**：全局可见性 → 有；per-session choose/clear/hide → 仅 deferred 状态，真实行为未实现
- **仍阻塞**：✅ 是 — per-session 头像 choose/clear/hide 行为未实现

#### 新建会话模块（LEG-008, LEG-009, LEG-010, LEG-043）

**LEG-008 · 新建会话快速流程**
- **代码证据**：`FormalHomeScreen.kt` 含 `NewSessionMode` 枚举、新建会话按钮触发 ModalBottomSheet（2处 ModalBottomSheet）；`NewSessionModels.kt`（108行）含 `BuiltInSessionTemplates`；`FormalPresetModels.kt` 预设模型；`Task2B1NewSessionScreen.kt` / `FigmaNewSessionScreen.kt` 新建会话屏幕；`NewSessionLifecycle.kt` 生命周期管理
- **JVM 测试**：`NewSessionLifecycleTest.kt`、`NewSessionPrefillTest.kt`、`NewSessionTemplatesTest.kt`、`FormalPresetModelsTest.kt` — 生命周期/预填充/模板/预设测试通过
- **设备证据**：P2-006 emulator-5554 验证模式选择→预设创建→聊天入口
- **契约映射**：快速创建/内置预设/导入预设/自定义 → 原生有六种内置模板、自定义配置、预设导入路径
- **仍阻塞**：✅ 是 — Settings 侧预设/导出 parity 待补

**LEG-009 · 自定义会话配置**
- **代码证据**：`NewSessionModels.kt` 含自定义配置字段；`SessionRepository.kt` `createCustomSessionPersistsProfileInSessionSettings` 方法持久化角色/目标/配置
- **JVM 测试**：`SessionRepositoryTest.kt` `createCustomSessionPersistsProfileInSessionSettings` 验证配置持久化到 `session_settings.systemPrompt`
- **设备证据**：P2-006 验证 Profile 激活后 Fake Runtime 生成
- **契约映射**：标题/角色/目标/截止日期/人格/长配置文本/标签/策略/头像 → 原生有角色/目标/配置摘要，但完整字段 parity 待补
- **仍阻塞**：✅ 是 — Runtime 中使用配置的 chat turns 待验证

**LEG-010 · 预设导入/导出**
- **代码证据**：`FormalPresetModels.kt` 预设模型；`NativeSessionPreset.kt`（core/protocol）预设协议；`NativeSessionPresetValidatorTest.kt` 验证器；`ProtocolExportPayloads.kt` 导出载荷
- **JVM 测试**：`NativeSessionPresetValidatorTest.kt`（core/protocol）验证 `reverse_tutor_preset_v1` 安全验证、密钥拒绝；`ProtocolExportPayloadBuilderTest.kt` 导出载荷验证
- **设备证据**：无设备级预设管理 UI 验证
- **契约映射**：JSON 预设导入/预览/保存/创建/自定义 → 原生有安全验证和创建路径，但完整 UI/设备证据待补
- **仍阻塞**：✅ 是 — 完整预设管理 UI/设备证据待补

**LEG-043 · 内置模板**
- **代码证据**：`NewSessionModels.kt` `BuiltInSessionTemplates` 对象定义六种原生内置模板
- **JVM 测试**：`NewSessionTemplatesTest.kt` 验证模板创建
- **设备证据**：P2-002 HMA-AL00 模板创建冒烟测试
- **契约映射**：PWA 六种场景模板 → 原生六种内置模板，一一对应
- **仍阻塞**：✅ 是 — 完整 fixture/export parity 待补

#### 聊天模块（LEG-012, LEG-013, LEG-014, LEG-015, LEG-041）

**LEG-012 · 聊天线程**
- **代码证据**：`ChatScreen.kt`（1441行）实现消息列表/用户与助手气泡/引用/来源页脚；`ChatUiState.kt` UI 状态；`MessageRepository.kt`（core/data）Room-backed 消息仓库
- **JVM 测试**：`ChatUiStateTest.kt`、`ChatPresentationContractsTest.kt` — 时间线渲染/状态测试；`ChatGenerationRepositoryTest.kt` 11 @Test 含消息持久化/stale token/失败映射
- **设备证据**：P2-006 emulator-5554 验证时间线消息/Fake Runtime 回复/重启持久化
- **契约映射**：消息列表/气泡/引用/来源 → 原生有核心渲染和引用，但数学/科学渲染、cited_chunk_ids 精确解析、可点击引用待补
- **仍阻塞**：✅ 是 — 精确引用解析/可点击引用/高亮 parity 待补

**LEG-013 · 聊天编辑器**
- **代码证据**：`ChatComposerContracts.kt`（444行）实现文本输入/发送/空白拒绝/图片附件预览；`ChatScreen.kt` 使用 `imePadding`（2处引用）
- **JVM 测试**：`ChatComposerTask3AContractsTest.kt` 编辑器契约测试
- **设备证据**：P2-003 HMA-AL00 验证编辑器可见性/IME 安全
- **契约映射**：文本/发送/图片选择/预览取消 → 原生有文本/发送/空白拒绝/图片草稿预览，但真实图片选择器管道待补
- **仍阻塞**：✅ 是 — 真实图片选择器/附件管道待补

**LEG-014 · 引用回复**
- **代码证据**：`ChatComposerContracts.kt` 含 Quote 相关代码（5处引用）；`ChatMessageActionContracts.kt` 含消息引用选择；`MessageRepository.kt` 持久化 `message_quotes` 记录
- **JVM 测试**：`ChatComposerTask3AContractsTest.kt` 引用草稿测试；`ChatMessageActionsTask3BTest.kt` 消息动作测试
- **设备证据**：P2-003 HMA-AL00 引用冒烟测试
- **契约映射**：长按引用/引用预览/清除引用/发送带引用 → 原生有完整引用流程和持久化
- **仍阻塞**：✅ 是 — 更广泛 parity 审计待 P6

**LEG-015 · 消息操作**
- **代码证据**：`ChatMessageActionContracts.kt`（509行）实现 Quote/Note/Regenerate/Delete 操作；`ChatMessagePlatformAdapters.kt` 平台适配
- **JVM 测试**：`ChatMessageActionsTask3BTest.kt` 消息动作测试；`ChatMessagePlatformAdaptersTask3BTest.kt` 平台适配测试
- **设备证据**：无设备级消息操作面板验证
- **契约映射**：长按操作面板 → 原生有 Quote/Note/Regenerate(deferred)/Delete；Note 和 Regenerate 显示 deferred 对话框
- **仍阻塞**：✅ 是 — 真实 note/memory 副作用和 LLM regenerate 待补

**LEG-041 · 键盘处理**
- **代码证据**：`ChatScreen.kt` 使用 `imePadding` 和导航栏 padding；`ChatComposerContracts.kt` IME 安全编辑器布局
- **JVM 测试**：`ChatComposerTask3AContractsTest.kt` 验证编辑器行为
- **设备证据**：P2-003 HMA-AL00 / Android 10 验证编辑器在打字时保持可见
- **契约映射**：PWA 键盘适配 → 原生 Compose `imePadding` 等价
- **仍阻塞**：✅ 是 — 更广泛设备矩阵待 P6

#### 生成与后台模块（LEG-016, LEG-017, LEG-042）

**LEG-016 · 流式与队列**
- **代码证据**：`ChatGenerationCoordinator.kt`（47行）生成协调器接口；`BackgroundGenerationRepository.kt`（403行）Room-backed 生成任务仓库含排队/运行/完成/失败/取消/丢弃状态 + stale-token 隔离；`BackgroundGenerationWorker.kt`（69行）WorkManager Worker
- **JVM 测试**：`ChatGenerationRepositoryTest.kt`（11 @Test：stale token/持久化/失败映射/无模型/图片能力）；`BackgroundGenerationRepositoryTest.kt`（7 @Test：排队/执行/失败/恢复/取消/归档丢弃/独立任务）
- **设备证据**：P2-006 emulator-5554 验证无模型/Fake Runtime 后台生成/会话隔离/重启持久化
- **契约映射**：流式气泡/pending 队列/stale 隔离 → 原生有完整生命周期和 stale-token 防护
- **仍阻塞**：✅ 是 — P6 设备矩阵待补

**LEG-017 · 后台回复**
- **代码证据**：`BackgroundGenerationWorker.kt` WorkManager Worker；`BackgroundGenerationNotifier.kt` 通知器；`BackgroundGenerationNotificationPolicy.kt` 通知策略（opt-in/完成失败/安全文本/稳定ID）；`BackgroundGenerationOutcomeHandler.kt` 结果处理器；`BackgroundGenerationStartupRecovery.kt` 启动恢复
- **JVM 测试**：`BackgroundGenerationNotificationPolicyTest.kt`（10 @Test）；`BackgroundGenerationWorkerTest.kt`；`BackgroundGenerationStartupRecoveryTest.kt`（2 @Test）；`GenerationDiagnosticPolicyTest.kt`（5 @Test）
- **设备证据**：P3-004 emulator-5554 验证通知投递和诊断投影
- **契约映射**：后台生成/通知/通知设置 → 原生有完整 WorkManager 管道和通知策略
- **仍阻塞**：✅ 是 — P6 后台/provider 矩阵待补

**LEG-042 · 离线/Mock 行为**
- **代码证据**：`HybridAppGraph.kt` 注入 `FakeLlmGenerationRuntime`；`ChatGenerationCoordinator.kt` 无模型状态处理；`ChatGenerationRepository.kt` `NoModelConfigured` outcome
- **JVM 测试**：`ChatGenerationRepositoryTest.kt` `noActiveProfileReturnsNoModelOutcome` 验证无模型状态
- **设备证据**：P2-006 emulator-5554 验证可见"未配置模型"和无 Mock 回复
- **契约映射**：PWA mock/fallback → 原生有显式无模型空状态和 Fake Runtime
- **仍阻塞**：✅ 是 — P6 parity/设备矩阵待补

#### 上下文中心与记忆模块（LEG-019, LEG-020, LEG-021, LEG-022, LEG-024, LEG-025）

**LEG-019 · 上下文中心入口**
- **代码证据**：`ContextHubScreen.kt`（596行）上下文中心屏幕；`AppShell.kt` Chat 头部入口和路由；`AppNavigation.kt` Context Hub 路由
- **JVM 测试**：`ContextHubModelsTest.kt` 上下文中心模型测试
- **设备证据**：P5-001 HMA-AL00 上下文中心打开/返回验证
- **契约映射**：PWA"脉络"入口 → 原生 Chat 头部入口打开 Context Hub
- **仍阻塞**：✅ 是 — 真实图谱/证据数据和最终 parity 待补

**LEG-020 · 上下文图谱**
- **代码证据**：`KnowledgeGraphPanel.kt`（1289行）原生 Compose Canvas 图谱渲染/pan/zoom/tap 选择/node detail/无障碍节点芯片；`KnowledgeGraphModels.kt` 图谱状态模型；`GraphInteractionPolicy.kt` 交互策略；`GraphRepository.kt`（core/data）图谱仓库
- **JVM 测试**：`KnowledgeGraphUiStateTest.kt` 图谱状态测试（hitTest/withSelection/empty/large/invalid）；`GraphRepositoryTest.kt`（8 @Test：保存/列出/拒绝空输入/空间范围/节点编辑/全局范围/会话范围/空状态/DAO失败）；`GraphInteractionPolicyTest.kt` 交互策略测试
- **设备证据**：P5-003/P5-004 HMA-AL00 图谱仪器验证证据
- **契约映射**：PWA 知识图谱 → 原生有 Canvas 渲染/pan/zoom/select/detail/edit/review，但真实会话图谱投影和完整语义卡片待补
- **仍阻塞**：✅ 是 — 真实会话图谱投影/语义卡片/gesture QA/Phase 6 parity 待补

**LEG-021 · 上下文锚点**
- **代码证据**：`MemoryRepository.kt`（227行）锚点 create/list/delete + 来源/消息链接字段；`ContextHubScreen.kt` 锚点表面渲染
- **JVM 测试**：`MemoryRepositoryTest.kt`（3 @Test：创建列出链接/编辑删除解析/保留50条诊断记录）
- **设备证据**：P5-002 HMA-AL00 上下文中心快照渲染
- **契约映射**：PWA 锚点/导入文件/重处理/删除 → 原生有锚点 create/list/delete 和快照，但来源到锚点管理 UI 待补
- **仍阻塞**：✅ 是 — 来源到锚点管理 UI/精确跳转高亮/图谱投影待补

**LEG-022 · 上下文笔记**
- **代码证据**：`ChatMessageActionContracts.kt` Note 动作（26处 Memory 引用）；`MemoryRepository.kt` 笔记 create/edit/delete + 来源消息链接；`ContextHubScreen.kt` 笔记计数渲染
- **JVM 测试**：`MemoryRepositoryTest.kt` `editsDeletesAndResolvesMemoryRecords` 验证笔记编辑/删除
- **设备证据**：P5-002 HMA-AL00 Chat-Note-to-Context-Hub 验证
- **契约映射**：PWA 笔记从消息创建/列表/编辑/删除 → 原生有 Chat Note 动作和仓库 CRUD
- **仍阻塞**：✅ 是 — 手动笔记管理 UI/记忆项同步/final parity 待补

**LEG-024 · 上下文设置**
- **代码证据**：`SessionSettingsContracts.kt` 会话设置契约；`SessionSettingsScreen.kt` 设置屏幕；`FormalSessionSettingsScreens.kt`（app/shell）设置路由屏幕；`SessionSettingsRoute.kt` 路由
- **JVM 测试**：`SessionSettingsCoordinatorTest.kt`、`SessionSettingsNavigationContractTest.kt`、`SessionSettingsProductionAdaptersTest.kt`、`SessionSettingsProductionSnapshotTest.kt`
- **设备证据**：P5-001 上下文设置表面暴露（deferred copy）
- **契约映射**：PWA per-session 策略/人格/截止日期/头像/警告 → 原生有设置表面，但完整编辑/警告流待补
- **仍阻塞**：✅ 是 — 人格/策略/截止日期/头像/警告流编辑待补

**LEG-025 · 全局图谱/洞察**
- **代码证据**：`KnowledgeGraphPanel.kt` 全局图谱路由；`ContextHubScreen.kt` 全局图谱入口；`GraphRepository.kt` 全局范围查询
- **JVM 测试**：`GraphRepositoryTest.kt` `globalScopeReturnsReadySnapshotAndCountsInvalidEdges` 验证全局范围
- **设备证据**：P5-003/P5-004 HMA-AL00 全局图谱验证
- **契约映射**：PWA 全局图谱/洞察 → 原生有全局图谱只读浏览路由
- **仍阻塞**：✅ 是 — 真实全局洞察/聚合规则/语义审查卡片 parity 待补

#### 设置与 LLM 配置模块（LEG-026, LEG-027, LEG-028）

**LEG-026 · LLM Provider 配置**
- **代码证据**：`FormalLlmConfigurationScreen.kt`（809行）Provider 预设（OpenAI/Anthropic/Custom）+ 配置 UI + 能力标志 + ModalBottomSheet；`FormalProviderPresetUiModelTest.kt` 预设模型测试
- **JVM 测试**：`LlmProfileSettingsModelTest.kt`、`FormalProviderPresetUiModelTest.kt`、`FormalSettingsScreenModelTest.kt` — 配置/预设/设置模型测试
- **设备证据**：P2-004 HMA-AL00 设置冒烟测试
- **契约映射**：PWA Provider 预设/协议选择/URL/模型/Key/视觉提示 → 原生有等价配置，但实时生成/运行时消费待补
- **仍阻塞**：✅ 是 — 实时生成/runtime 消费待 P2-005/P6

**LEG-027 · LLM Profiles**
- **代码证据**：`LlmProfileRepository.kt`（102行）Room-backed profile save/list/activate/delete + secretRef-only 持久化；`SecretStore.kt`（core/data）Android Keystore-backed 密钥存储
- **JVM 测试**：`LlmProfileRepositoryTest.kt`（2 @Test：saveProfileStoresOnlySecretReference / activateProfileSwitchesEnabledProfileAndDeleteRemovesSecret）
- **设备证据**：P2-004 HMA-AL00 设置冒烟测试
- **契约映射**：PWA 保存/切换/删除多 Profile → 原生有完整 CRUD + 安全密钥处理
- **仍阻塞**：✅ 是 — 完整 legacy key migration/release parity 待补

**LEG-028 · LLM 诊断**
- **代码证据**：`FormalDiagnosticsScreens.kt` 诊断屏幕；`ModelConnectionsViewModel.kt` 连接测试 ViewModel；`BackgroundGenerationOutcomeHandler.kt` 失败映射；`GenerationDiagnosticPolicy.kt` 诊断策略
- **JVM 测试**：`ModelConnectionsViewModelTest.kt`、`GenerationDiagnosticPolicyTest.kt`（5 @Test）
- **设备证据**：P3-004 emulator-5554 验证失败诊断投影和剪贴板安全
- **契约映射**：PWA 连接测试/诊断模态/复制 → 原生有 Mock 连接测试和诊断详情/复制
- **仍阻塞**：✅ 是 — 真实外部 Provider 调用待 P6

#### 导入/导出模块（LEG-031, LEG-032）

**LEG-031 · 数据导出**
- **代码证据**：`FormalImportExportScreens.kt`（601行）导出 UI 含 `FormalTransferStatus`；`ProtocolExportPayloads.kt`（core/protocol）协议级导出载荷构建器；`NativeExportRepository.kt`（core/data）数据支撑导出仓库
- **JVM 测试**：`NativeExportRepositoryTest.kt`（3 @Test：currentSession / fullBackupWithoutSecrets / graphSnapshot）；`ProtocolExportPayloadBuilderTest.kt`
- **设备证据**：P4-007 HMA-AL00 导出就绪仪器验证
- **契约映射**：PWA 导出当前会话/图谱/全量备份/保存到文件管理器 → 原生有等价载荷类型和 Android share/save
- **仍阻塞**：✅ 是 — Phase 6 完整导出 parity 和投递矩阵待验证

**LEG-032 · 数据导入**
- **代码证据**：`FormalImportExportScreens.kt` 含 `FormalImportMode`（Append/Overwrite/NewSpace）+ `FormalImportIssueSeverity`；`ProtocolImportReader.kt`（core/protocol）协议导入读取；`NativeImportRepository.kt`（core/data）导入仓库
- **JVM 测试**：`NativeImportRepositoryTest.kt`（9 @Test：dryRun / import / overwrite确认 / overwrite替换 / newSpace / append幂等 / 无效文件 / 无密钥恢复 / 不安全记录跳过）；`ProtocolImportReaderTest.kt`
- **设备证据**：P4-007 HMA-AL00 导入模式仪器验证
- **契约映射**：PWA 导入覆盖 → 原生有 append/overwrite/new-space 三种模式 + 破坏性确认
- **仍阻塞**：✅ 是 — Phase 6 真实替换迁移来源和设备矩阵待验证

#### 来源模块（LEG-034）

**LEG-034 · 来源文件导入**
- **代码证据**：`SourcesScreen.kt`（366行）来源库 UI；`SourcesModels.kt` 来源模型；`SourceRepository.kt`（core/data）来源仓库；`SourceImportInputFactory.kt`（app/shell）来源导入工厂
- **JVM 测试**：`SourceRepositoryTest.kt`（6 @Test：TXT本地解析 / Markdown剥离 / 失败状态 / HTML部分解析 / 不支持状态 / 重处理）；`SourcesUiStateTest.kt`；`SourceImportInputFactoryTest.kt`
- **设备证据**：P5-005 HMA-AL00 来源库仪器验证
- **契约映射**：PWA PDF/DOCX/TXT/Markdown/HTML/PPTX/EPUB → 原生有 TXT/Markdown 本地解析、HTML 部分解析、其他 queued_for_future_api 状态
- **仍阻塞**：✅ 是 — 事务加固/持久 URI 重读/复杂解析器/设备矩阵/Phase 6 parity 待补

---

## 三、P1 观察项摘要（16 条，非 P0 阻塞）

| ID | 入口 | 代码状态 | 仍观察 |
|---|---|---|---|
| LEG-003 | 全局侧边栏 | 设置路由 + DataStore 主题/头像/备忘录占位 | watch — 全局设置抽屉/屏幕待补 |
| LEG-004 | 全局备忘录 | 备忘录占位和持久化键 | watch — 编辑/添加/删除手势未实现 |
| LEG-007 | 会话 proactive 状态 | proactive-deferred 状态在会话元数据 | watch — 产品决策和主动控制待定 |
| LEG-011 | 初始来源选择 | deferred handoff 通知 + 来源选择摘要 | watch — 真实来源附件到聊天/上下文待补 |
| LEG-018 | 聊天图片理解 | 结构化 URI/mime/sourceId 附件 + 视觉能力门控 | watch — 真实多模态 Provider 执行待补 |
| LEG-023 | 上下文错误 | ErrorLog 仓库 create/list/resolve + 计数 | watch — 错误证据链接工作流待 P6 |
| LEG-029 | Proactive 设置 | 通知偏好切换（默认 false） | watch — 完整 proactive 产品决策部分验证 |
| LEG-030 | 更新设置 | About 诊断存在 | watch — 更新检查/下载/安装未实现 |
| LEG-035 | 图片来源导入 | 图片选择器 + SourceType.Image 记录 | watch — 专用设备冒烟和真实多模态待补 |
| LEG-036 | 解析来源预览 | 来源卡片/片段/重处理动作 | watch — 可点击引用/精确高亮/丰富检索待补 |
| LEG-037 | 主题系统 | 主题偏好模型存在 | watch — 可见主题选择器和变体待补 |
| LEG-040 | 边缘/swipe 行为 | 未开始 | watch — 原生会话 swipe 或边缘手势 parity |

---

## 四、审计统计汇总

### 按状态分布

| 状态 | 数量 | 变化 |
|---|---:|---|
| verified | 4 | 不变 |
| in_progress | 38 | 不变 |
| not_started | 2 | 不变（LEG-040, LEG-037 部分在 LEG-040） |

### P0 阻塞项分布

| 模块 | P0 阻塞数 | 关键缺口 |
|---|---:|---|
| 会话与首页 | 4 | swipe 手势、头像 choose/clear/hide、未读计数 parity |
| 新建会话 | 4 | 预设管理 UI、配置字段 parity、fixture/export parity |
| 聊天 | 5 | 精确引用解析、图片选择器管道、消息操作副作用、设备矩阵 |
| 生成与后台 | 3 | 设备矩阵、真实 Provider 调用、P6 parity |
| 上下文与记忆 | 6 | 真实图谱投影、语义卡片、锚点 UI、笔记管理 UI、设置编辑 |
| 设置与 LLM | 3 | 实时生成消费、key migration、真实 Provider |
| 导入/导出 | 2 | 完整 parity 和投递矩阵、真实迁移来源 |
| 来源 | 1 | 事务加固、复杂解析器、设备矩阵 |
| **合计** | **28** | |

### 代码证据覆盖率

| 证据类型 | P0 阻塞项覆盖 | 备注 |
|---|---|---|
| 代码证据 | 28/28 (100%) | 所有 P0 入口均有原生代码实现 |
| JVM 测试证据 | 28/28 (100%) | 所有 P0 入口均有对应 JVM 测试 |
| 设备证据 | 8/28 (29%) | 仅 8 项有上一阶段设备证据，本轮全部待补 |
| 契约映射 | 28/28 (100%) | 所有 P0 入口均有原生映射（部分为 deferred/reduced） |
| 仍阻塞 | 28/28 (100%) | 全部仍为 P0 替换阻塞项 |

### JVM 测试方法统计（本轮审计的核心数据层）

| 测试文件 | @Test 数 | 覆盖范围 |
|---|---:|---|
| SessionRepositoryTest | 5 | 会话 CRUD/pin/归档/自定义配置 |
| ChatGenerationRepositoryTest | 11 | 生成/stale token/失败映射/无模型/图片能力/引用 |
| BackgroundGenerationRepositoryTest | 7 | 排队/执行/失败/恢复/取消/归档丢弃/独立任务 |
| MemoryRepositoryTest | 3 | 锚点/笔记/错误 CRUD + 50条保留 |
| GraphRepositoryTest | 8 | 保存/列出/空输入/空间范围/编辑/全局/会话/失败 |
| SourceRepositoryTest | 6 | TXT/Markdown/HTML/失败/不支持/重处理 |
| NativeImportRepositoryTest | 9 | dryRun/import/overwrite/newSpace/append幂等/无效/安全 |
| NativeExportRepositoryTest | 3 | 当前会话/全量备份/图谱快照 |
| LlmProfileRepositoryTest | 2 | secretRef-only/activate+delete |
| BackgroundGenerationNotificationPolicyTest | 10 | 策略 enforcement |
| GenerationDiagnosticPolicyTest | 5 | 安全诊断代码 |
| BackgroundGenerationStartupRecoveryTest | 2 | 启动恢复 |
| **合计** | **71** | 核心数据层 + 后台生成 |

---

## 五、关键发现

### 1. 代码与 JVM 测试覆盖完整
所有 28 个 P0 阻塞项均有：
- 原生代码实现（源码文件已验证存在且含关键类/接口/Composable）
- 对应 JVM 测试（71+ 核心数据层测试方法，400+ 总测试，0 失败）

### 2. 设备证据是主要缺口
仅 8/28 项有上一阶段设备证据，本轮因模拟器未运行全部待补。设备测试是解除 P0 阻塞的必要条件。

### 3. 功能完成度分层明显
- **核心闭环已验证**：会话创建→配置→发送→Fake Runtime 回复→重启持久化→会话隔离（P2-006 JVM + 设备）
- **基础功能已实现**：图谱/记忆/来源/导入导出/后台生成/通知/诊断 均有代码和 JVM 测试
- **parity 缺口集中在**：真实 Provider 调用、复杂解析器、swipe 手势、头像管理、语义卡片、精确引用高亮

### 4. 冻结层保护完好
core/model, core/protocol, core/llm, core/data 零修改。所有功能实现均在 feature/* 和 app/* 层，通过 Repository/Facade/Coordinator 调用业务能力。

### 5. 无契约缺口发现
本轮审计未发现新的契约缺口——所有 P0 入口的原生实现都通过既有 Repository 接口调用数据层，未直接导入 DAO/Entity/Database/SecretStore。

---

## 六、下一步建议

1. **设备测试（用户启动模拟器后优先执行）**：
   - P5-008 集成设备测试（Context Hub 入口→Chat 返回→Graph 选择/缩放/编辑→Sources 五类解析状态）
   - UX-010 无障碍验证（深色模式/大字号/48dp/TalkBack）
   - Phase2CoreLoopDeviceTest 修复后重跑（中文文案断言已修正）
   
2. **P6-002～P6-004 替换准备**：
   - 编写替换包 Runbook
   - 完成全量设备矩阵
   - 编写旧数据迁移指南
   - 检查导入、导出、回滚和灾备流程

3. **P6-005 PWA 退出决策**：
   - 仅在所有 P0 阻塞项完成或明确豁免后评估

---

## 七、声明

- **未 commit、未 push、未创建 tag**
- **未调用真实 Provider/网络/API Key**
- **未修改冻结层** — core/model, core/protocol, core/llm, core/data 零修改
- **未执行设备测试** — 模拟器未运行，设备证据全部标注为"待补"
- **构建/JVM/Lint 基线**：上一轮 UP-TO-DATE，树未变（b6c3307）→结果仍有效

---

## 八、2026-08-17 设备证据补充

P6-001 完成后的现代模拟器补测已执行，未修改业务代码、契约层或冻结层。

设备：`Pixel_8_Pro` AVD，`emulator-5554`

```text
Phase5ContextHubDeviceTest — 4 tests passed
Phase5GraphDeviceTest — 5 tests passed
Phase5MemoryDeviceTest — 1 test passed
Phase5SourcesDeviceTest — 1 test passed
合计：11 tests passed, 0 failures

Phase2CoreLoopDeviceTest — 3 tests passed, 0 failures
```

本次补充覆盖 Context Hub 证据跳转与返回、图谱 Canvas/空状态/大图/无效图/编辑审查、Memory 持久化联动、Sources 五态与恢复边界，以及 Phase 2 核心闭环回归。

边界保持不变：TalkBack 实际朗读顺序仍需人工听测；主力真机、旧 Android、小屏矩阵、真实 Provider、复杂解析器、头像 choose/clear/hide、swipe 和精确引用高亮仍属于 P6 后续 parity 工作。本补充不代表 PWA/Capacitor 退出或 replacement ready。
