# 本轮学习辅助面板 · 内嵌系统提示设计规格

> 状态：已确认，待前端实施计划
> 分支：`newmp`
> 责任：Track B 只实现 `feature/chat` 表现层；Track A 后续提供聊天宿主挂载点

## 1. 目标与范围

在聊天消息流中，为“本轮学习辅助”提供默认收起、低干扰的入口。入口挂在对应 AI 回复之后，使用斜体、浅灰色的系统提示形式。用户点击入口后，才打开可关闭的详情微面板。

该能力是聊天的辅助层，不是仪表盘、知识图谱、卡片墙或固定占高的侧栏。对话消息始终是主要视觉焦点。

本规格只定义 Compose 表现层和测试边界，不修改会话算法、策略、评估、掌握度、生成结果、Repository、DAO、Entity、Room、网络、LLM、数据契约、`AppShell.kt`、app wiring、core 模块或冻结层。

## 2. 唯一输入与输出

表现层只能读取：

```kotlin
SessionConversationContract?
(SessionAssistantInteraction) -> Unit
```

表现层只能向外派发 `RETRY`、`OPEN_CONTEXT`、`OPEN_SOURCE`、`SHOW_EVALUATION`、`DISMISS`。宿主负责重试、导航与详情路由；`DISMISS` 只关闭当前微面板，不取消生成、不删除会话、不改写状态。

禁止 UI 读取或生成 Repository、DAO、Entity、Database、SQL、SecretStore、`ChatGenerationInput`、Provider DTO、掌握度算法、教学策略、薄弱点、Token 或图谱关系。

## 3. 消息流锚定规则

1. 当 `contract.generation.assistantMessageId` 非空，宿主只在 timeline 的 `messageId` 等于该值的 AI 回复之后挂载入口。
2. 当该 ID 为空（无模型、失败、陈旧请求、会话删除），宿主在消息流末尾的系统提示区域挂载入口；不得伪造 AI 回复或借用旧回复。
3. `contract == null` 时不渲染入口或详情微面板。
4. 入口存在与否只能由契约的已有安全字段决定；不推断教学动作、掌握度或生成状态。
5. A4/Track A 负责真实 timeline 挂载；Track B 不修改 `AppShell.kt` 或装配链路。

## 4. 两层表现模型

### 4.1 默认收起的内嵌系统提示

新增 `SessionAssistantReplyHint`，作为可嵌入单条消息后的纯 Compose 组件。

- 文案固定为“本轮学习提示”或等价短文案；
- 使用斜体与低对比浅灰色，不模拟 AI 气泡；
- 可显示低饱和状态点，不用大面积警告、渐变或持续动画；
- 最小点击高度 48dp，提供中文 `contentDescription` 和稳定 `testTag`；
- 空内容不渲染占位行；
- 入口只展开本地详情状态，不直接执行重试或导航。

### 4.2 点击后出现的详情微面板

保留 `SessionAssistantPanel` 作为详情面板。它由入口内部的本地可保存状态控制，默认关闭；新 `sessionId + turnId` 到来时仍默认关闭，不自动弹出。

显示顺序固定为：生成状态与安全中文提示 → 本轮教学动作与下一步 → 简短评估摘要 → 前置缺口、关联记忆、资料证据、待复习知识点。

每个区块只在对应契约字段真实存在时渲染。禁止用“暂无数据”填满详情页，也禁止把助手回复全文复制成独立大卡片。

面板为干净、轻量的半屏底部面板：使用 `FormalColors`、`FormalShapes`、`LocalFormalTypeScale` 和既有间距规范；不使用玻璃拟态、厚重描边、多栏仪表盘、图谱画布或无限动画。若使用动画，只允许短促淡入/上浮，且不得阻塞 Compose 测试空闲状态。

## 5. 安全与失败显示

`ERROR`、`NO_MODEL`、`UNSUPPORTED`、`BLANK`、陈旧请求和会话删除只显示 `GenerationUiContract.safeError` 或已有固定中文文案。

入口、详情面板、语义描述与日志文本禁止显示 URL、Provider 名、模型名、请求内容、`Authorization`、`Bearer`、`sk-`、API key、SecretStore 引用、`Throwable.message`、原始 warning、堆栈、DAO/Entity/协议 DTO。

上下文 warning 必须继续走 `toSafeWarningTexts()` 的白名单映射；未知 warning 统一显示“部分学习数据暂不可用”。

## 6. 交互与无障碍

- 点击内嵌提示：只展开详情微面板；
- “重试”只派发 `RETRY`；“查看上下文”只派发 `OPEN_CONTEXT`；“查看资料”只派发 `OPEN_SOURCE`；“查看评估”只派发 `SHOW_EVALUATION`；
- 关闭、点击面板外或系统返回：关闭微面板并派发 `DISMISS`。

所有可操作控件最小 48dp。纯装饰图标的 `contentDescription` 为 `null`；可操作图标必须有中文描述。微面板内容可纵向滚动并能在大字号下换行，不使用固定高度或嵌套滚动容器。

## 7. 文件边界

Track B 允许修改：

- `mobile-native/feature/chat/src/main/java/com/reversetutor/feature/chat/SessionAssistantPanel.kt`
- `mobile-native/feature/chat/src/test/java/com/reversetutor/feature/chat/SessionAssistantPanelStateTest.kt`
- 新增 `mobile-native/feature/chat/src/main/java/com/reversetutor/feature/chat/SessionAssistantReplyHint.kt`
- 新增对应 feature/chat 测试文件。

Track B 禁止修改：`app/shell/AppShell.kt`、`app/wiring/**`、`core/**`、任意冻结层、Repository、Room、网络与 LLM 文件。

## 8. 测试要求

先写失败测试，至少覆盖：

1. `contract == null` 时无入口、无面板；
2. 有 `assistantMessageId` 时入口可作为对应回复后的 slot 内容；
3. 无 `assistantMessageId` 的安全失败状态在消息流末尾显示系统提示，不伪造 AI 消息；
4. 入口初始收起，点击后才显示微面板；新 `turnId` 到来不自动展开；
5. 空 action/evaluation/context 区块不渲染；
6. 五种 interaction 只派发相应的 `SessionAssistantInteraction`；
7. 系统提示有稳定 `testTag`、中文语义描述与至少 48dp 点击区；
8. 安全 warning 与失败态不含 URL、Provider、模型名、Authorization、`sk-` 或异常原文；
9. 大字号/长文本不裁剪，面板内容可滚动。

必跑命令：

```powershell
$env:ANDROID_HOME = 'C:\Users\Lenovo\AppData\Local\Android\Sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
.\gradlew.bat :feature:chat:testDebugUnitTest --console=plain
.\gradlew.bat :feature:chat:lint --console=plain
git diff --check
git diff --name-only -- mobile-native/core/model mobile-native/core/protocol mobile-native/core/llm mobile-native/core/data
```

最后一条必须无输出。设备级验收只在 A4 宿主接线完成后进行，不替代 JVM 与 lint 证据。

## 9. 后端缺口结论

本轮不需要新增后端接口或冻结层变更。现有 `SessionConversationContract` 已提供 `generation.assistantMessageId`、安全 generation 状态和 `safeError`、`action`、`nextStep`、`evaluation`、`context` 与稳定 `ConversationUiEvent`。

当前缺口是前端宿主挂载，不是数据契约缺口。若 A4 无法向 timeline 提供“消息后 slot”或无法在失败态提供“消息流末尾系统提示区域”，则由 Track A 提交非冻结 capability request；Track B 不得绕过宿主自行接入 `AppShell`。
