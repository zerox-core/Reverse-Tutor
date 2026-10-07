# Native 前端开发交接总览

> 分支：`newmp`（唯一生产分支；不要与 `Android` 合并）
> 读者：Track B / 前端实现者
> 维护者：Track A；任何新增能力或契约变更必须先由 Track A 发布，再由本文件更新。

## 1. 开工前必读

按以下顺序阅读；后面的文件不替代前面的约束。

1. `AGENTS.md`：协作硬规则、冻结范围、提交和测试规则。
2. `tasks/wave0-a3-freeze-boundary.md`：冻结层与变更审批门禁。
3. `tasks/wave0-a4-ownership-table.md`：物理文件所有权；混合文件先确认 owner。
4. `tasks/native-architecture-decision-record.md`：P1–P7 术语、Facade / Coordinator / Repository 的分层。
5. 本文件：前端可消费的能力、当前缺口与交接方式。
6. 需要视觉规范时读 `docs/mobile-ui-design-language.md`；需要在线/错误状态规范时读 `docs/contracts/mobile-native-integration-v1.md`。

如果设计需要这里未列出的字段、事件或数据，不得从 Repository、DAO、Entity、Database、SecretStore、协议 DTO 中自行取值；提交能力请求到 `tasks/capability-requests/`，并暂停该交叉边界改动。

## 2. 分层与不可触碰范围

前端负责 Compose 页面、视觉、布局、手势、无障碍语义、页面内状态和纯展示映射。前端只能消费已发布的 UiState、Facade 返回值和 Port；所有副作用经回调离开 UI。

以下范围冻结，除非得到用户针对该次变更的明确批准：

- `mobile-native/core/model/`
- `mobile-native/core/protocol/`
- `mobile-native/core/llm/`
- `mobile-native/core/data/*Repository/`
- `mobile-native/core/data/local/`、Room schema 与 migration
- `mobile-native/core/data/preferences/`、`SecretStore.kt`

同时不得修改 `app/wiring`、`HybridAppGraph.kt`、`BackgroundGenerationWorker.kt`、`AppShell.kt` 的业务装配或后台语义。若页面必须挂载新 Port 或新回调，由 Track A 完成。

## 3. 已发布、可直接消费的会话契约

### 3.1 会话辅助契约

- 类型：`mobile-native/feature/chat/src/main/java/com/reversetutor/feature/chat/SessionConversationContract.kt`
- 投影组件：`SessionAssistantPanel.kt`
- 交互枚举：`SessionAssistantInteraction`

`SessionConversationContract` 是会话算法的 UI 唯一事实源。它包含生成状态、受控错误、教学评估、下一步 action、上下文引用、稳定 event id 和消息列表。页面只能展示它，不能自行推断“掌握度”“错误类型”“应追问还是提示”。

前端必须遵守：

- `generation` 的显示分支来自契约状态；Provider 原始异常、URL、Authorization、模型名、密钥不可显示或记录。
- `warnings` 的 `source/message` 不直接显示；用白名单中文文案映射。
- 交互仅派发既有 `SessionAssistantInteraction`；`RETRY`、`OPEN_CONTEXT`、`OPEN_SOURCE`、`SHOW_EVALUATION`、`DISMISS` 的业务去向由宿主/Track A 处理。
- `null` contract 表示隐藏辅助内容，不得伪造“算法已执行”的卡片。

### 3.2 首页学习概览契约

- 领域读模型：`mobile-native/core/domain/src/main/java/com/reversetutor/core/domain/LearningOverviewContracts.kt`
- 前端状态与 factory：`mobile-native/feature/chat/src/main/java/com/reversetutor/feature/chat/LearningOverviewViewModel.kt`
- 展示组件：`mobile-native/feature/chat/src/main/java/com/reversetutor/feature/chat/LearningOverviewPanel.kt`

首页副屏只消费 `LearningOverviewUiState`。`null` 或空集合是合法的“暂无数据”，不能呈现为网络/数据库错误；错误状态只显示稳定安全文案。当前会话范围仅在宿主提供非空 `currentSessionId` 时出现，不能用空列表代表“当前会话”。

### 3.3 聊天编辑器与状态

- 状态/发送防抖：`ChatComposerContracts.kt`
- 聊天路由：`ChatScreen.kt`
- 当前前端内联提示（并行工作，未作为后端依赖）：`SessionAssistantReplyHint.kt`

发送成功后用户消息已经持久化；前端不得再次保存同一消息、不得直接调用 LLM、不得创建第二个后台任务。消息发送失败时仅保留草稿并按既有 `ChatSendAttempt` 显示安全失败状态。

## 4. 正在对接的后端能力与前端准备项

### 4.1 后台会话预处理（Track A 开发中）

设计来源：`docs/superpowers/specs/2026-08-23-background-turn-preparation-design.md`。

目标链路为：

```text
ChatRoute
  → ChatSendCoordinator（唯一用户消息写入）
  → BackgroundTurnPreparationPort（上下文 + 策略 + 入队）
  → BackgroundGenerationWorker（唯一 LLM 运行和 assistant 写入）
```

前端现在不需要实现新页面。准备要求如下：

- 继续根据现有 `ChatGenerationUiState.Pending` 显示“处理中”；不要自己轮询 Worker、不要假设即时 assistant 回复。
- 预处理器准备完成后会由 Track A 以 Port/安全结果接入 `ChatRoute`；前端只接收加载、终态刷新与已发布的 `SessionConversationContract`。
- 当前没有正式的会话模式字段。Track A 本轮按用户确认固定采用 `study` 策略；前端**不得**从 `dialogueStrategy` 的中文自由文本推断 `study/goal/companion`。
- 日后新增模式选择时，前端应传递明确的稳定 wire value `study`、`goal` 或 `companion`，并等待 Track A 发布对应输入契约；不要用显示中文作为 wire value。

### 4.2 尚未发布的前端需求

以下项目不能自行实现为“看似可用”的业务能力：

| 需求 | 当前状态 | 正确动作 |
|---|---|---|
| 切换 `study / goal / companion` | 未发布 UI 输入契约 | 提交 capability request，等待 Track A 发布字段与默认/兼容策略 |
| 直接展示后台任务的策略快照 | 未发布 UI read model | 只展示 `SessionConversationContract`，不要读取 Background Job / Room |
| 重新触发后台生成 | 需幂等与任务状态语义 | 只派发现有 retry interaction；无接口时申请能力 |
| 模型/Provider 诊断详情 | 敏感边界 | 只展示安全状态码映射；不得展示配置、URL、异常原文 |
| 从图谱、记忆、资料自行拼接提示词 | 后端聚合职责 | 使用上下文入口与既有导航回调；不得在 UI 聚合证据 |

## 5. 前端文件所有权与并行协作

当前并行的会话辅助视觉工作仅限下列 5 个文件；Track A 不会覆盖它们，Track B 也不得在其中接 Repository 或 app wiring：

- `mobile-native/feature/chat/src/main/java/com/reversetutor/feature/chat/SessionAssistantPanel.kt`
- `mobile-native/feature/chat/src/main/java/com/reversetutor/feature/chat/SessionAssistantReplyHint.kt`
- `mobile-native/feature/chat/src/test/java/com/reversetutor/feature/chat/SessionAssistantPanelStateTest.kt`
- `mobile-native/feature/chat/src/test/java/com/reversetutor/feature/chat/SessionAssistantInteractionTest.kt`
- `mobile-native/feature/chat/src/test/java/com/reversetutor/feature/chat/SessionAssistantReplyHintTest.kt`

`ChatScreen.kt`、`AppShell.kt` 和 `HybridAppGraph.kt` 是跨轨热点。需要接线时，Track B 先写明所需输入/事件及验收条件，再由 Track A 在独立提交中完成；两轨不得同时修改同一文件。

## 6. 前端验收要求

每个前端切片至少提供：

1. 状态测试：加载、空数据、成功、失败、不可用/降级状态均有稳定断言。
2. 无障碍：可点击控件不小于 48dp，必要元素有稳定 semantics/testTag，内容在大字号下不遮挡。
3. 安全：只显示白名单中文文案；不显示原始 Provider/网络/密钥/异常信息。
4. 边界：不新增 Repository、DAO、Entity、Database、SecretStore 或协议 DTO 的 UI 直接访问。
5. 最小验证：`./gradlew.bat :feature:chat:testDebugUnitTest :feature:chat:lint --console=plain`。

提交前执行 `git diff --check`，并附上实际改动文件、测试结果、仍未做的设备验证。若修改影响宿主挂载，再通知 Track A 做装配与真实会话 id 的联调。

## 7. 交接模板

```markdown
## 前端切片交接

- 参考契约：<文件路径和类型>
- 改动文件：<逐个路径>
- 未触碰范围：core/model、core/protocol、core/llm、core/data、app/wiring、Repository/DAO/Entity/Database/SecretStore
- 消费的输入：<UiState / Contract / Event>
- 派发的事件：<回调名称和预期宿主行为>
- 已验证：<命令 + 实际结果>
- 待 Track A 接线：<若无则写无>
- 未验证的设备项：<明确列出；不可用“已验证”代替>
```
