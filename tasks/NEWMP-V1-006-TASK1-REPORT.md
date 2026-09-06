# NEWMP-V1-006 Task 1 执行报告：可见文本与单回合节奏

日期：2026-09-06

## 目标

先修复原分支已经具备、而 newmp 真机暴露的聊天体验回退：内部教学控制不能进入聊天气泡；单回合需要有界，并明确要求模型只完成一个教学动作、最多留下一个教师问题。

## Red

新增两类测试：

- `LlmAssistantReplyEnvelopeTest.visibleTimelineTextDropsInternalTeachingLabelsAndBoundsLongTurns`
- `ChatGenerationRepositoryTest.plainProviderReplyIsSanitizedBeforeVisiblePersistence`

Red 真实暴露了两个缺口：结构化 envelope 的可见投影保留了 `Teaching policy`、`Action`、`Knowledge point` 等控制行；普通 Provider 文本旁路直接写入消息，没有统一长度边界。

## Green 实现

修改：

- `core/llm/LlmGenerationLifecycle.kt`
  - 新增统一 `toVisibleTimelineText()` 边界；过滤已知内部控制行，限制可见文本为 1200 字符，空结果使用安全学生式兜底。
  - `LlmAssistantReplyEnvelope.timelineText()` 统一经过该边界。
  - `reverseTutorStudentPromptBlock()` 增加单回合约束：一次只完成一个教学动作，最多三段/四行，最多一个教师问题，不自行回答该问题。
- `core/data/llm/ChatGenerationRepository.kt`
  - envelope 解析失败的普通文本也经过同一可见文本边界后再持久化。
- 测试文件：补充可见文本、普通 Provider 旁路和 prompt 约束断言。

结构化 outcome、checkPlan、evidence 仍通过原有独立字段保存，没有把学习状态并入聊天正文，也没有新增 assistant 写入路径。

## 验证证据

- 定向：`core:llm` 的 envelope/prompt 测试与 `core:data` 的 ChatGenerationRepository 测试 BUILD SUCCESSFUL。
- 受影响模块：`:core:llm:testDebugUnitTest :core:data:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug` BUILD SUCCESSFUL。
- `git diff --check`：无空白错误；输出中的 LF→CRLF 仅为既有 Windows 行尾提示。
- 冻结路径：`core:model`、`core:protocol`、`core:data/preferences`、SecretStore 无差异。
- 真机：Huawei BRA-AL00，API 31；Debug APK 覆盖安装成功，宿主重新启用并启动，`mResumedActivity` 为 `com.reversetutor.preview/.MainActivity`。
- 未运行 instrumentation，未卸载 APK，未清理应用数据。

## 当前边界

这一步解决的是“最终可见文本不泄露内部字段、单回合表达受约束”。它还没有实现真正的逐 chunk 流式 UI，也没有把一条回复拆成多个持久化 assistant 消息；这两项进入 Task 2，必须继续保持 Worker 单一最终写入者。

## 下一步

Task 2：以原分支 `5ce1e1d` 的 streaming/multi-message 行为为基线，先用 Fake runtime 钉住 chunk → 临时 UI → 单一最终 artifact 的链路，再接真实 Provider 流式响应。期间不改数据库 schema，不增加第二个 assistant 写入路径。
