# NEWMP-V1-006 Task 4 报告：多模态能力配置与图片输入

## 结论

Task 4 完成。多模态图片输入链路经逐项核对已具备：能力识别（qwen3.7-flash 白名单）、执行期图片转换（content:// → 有界 base64）、三家协议字段、不支持/超大图片安全失败、UI 提示与恢复幂等。本任务补齐缺失的 Anthropic/Gemini 协议字段 payload 测试与超大图片安全失败测试（3 个新用例），全量 JVM 测试通过（BUILD SUCCESSFUL）。

## 逐项核对结果

### 1. capability 测试（已存在，复验通过）

`LlmProfilePolicyTest.approvedQwenFlashVisionProfileIsRecognizedWithoutEnablingAllQwenModels`：`qwen3.7-flash-2026-07-15` → `supportsVision=true`；未声明的 `qwen-text-only` → `false`。识别实现为 `LlmProfileCapabilityResolver.infer`（保守 profile 前缀识别 `model.startsWith("qwen3.7-flash")`），未新增可编辑、可持久化 `supportsVision` 字段，未改任何表——符合「当前阶段保守识别、改表须先迁移设计」的约束。

### 2. payload 无泄露测试（OpenAI 已存在，本任务补齐 Anthropic/Gemini/超大）

- 已存在：`ProductionLlmGenerationRuntimeTest.localImageUriIsResolvedToProviderPayloadWithoutLeakingItsUri`——`content://private/image` 不出现在请求体，替换为 `data:image/png;base64,…`。
- 新增：`anthropicImageAttachmentUsesProtocolBase64SourceWithoutLeakingUri`——请求体含 `"type":"base64"`、`"media_type":"image/png"`、base64 数据，不含 `content://` URI。
- 新增：`geminiImageAttachmentUsesInlineDataWithoutLeakingUri`——请求体含 `inline_data`、`"mime_type":"image/png"`、base64 数据，不含 `content://` URI。

### 3. Fake multimodal runtime 测试

- 支持图片成功：`ChatGenerationRepositoryTest.imageAttachmentUsesInferredVisionCapabilityWhenInputOmitsCapabilities`（生成并持久化单条 assistant）。
- 不支持图片返回安全错误：`ChatGenerationRepositoryTest.unsupportedVisionAndBlankPromptReturnBlockedOutcomes`（`UnsupportedVision` outcome，不持久化）；`LlmGenerationPlanner` 在 `supportsVision=false` 且含图片附件时返回 `Blocked(UnsupportedVision)`；UI 映射 `ChatUiState.kt:224`「当前模型不支持图片输入」。
- 图片过大被拒绝：新增 `oversizedOrUnresolvableImageFailsSafelyWithoutTransportExecution`——resolver 返回 null（对应 `AndroidImagePayloadResolver` 超过 20MB 上限或不可读）时返回 `Failure("Provider configuration is invalid.", retryable=false)` 且**不发起任何传输请求**。
- 重启恢复不重复发送：`BackgroundGenerationRepositoryTest.recoveryRequeuesRunningJobsAndCancellationPreventsExecution` 与 `ConversationRunCoordinatorTest.duplicateCompletionIsAcceptedOnlyOnce` 固化恢复幂等；token 过期不落 assistant（`staleGenerationTokenDoesNotPersistAssistantMessage`）。

### 4. 执行期 resolver（已存在，复验通过）

`AndroidImagePayloadResolver`（core/data）：仅接受 `image/*` MIME 与 `content://` scheme；读取字节上限 20MB（`MaxImageBytes`）；`Base64.NO_WRAP` 编码。`ProductionLlmGenerationRuntime.generate` 在附件非空时强制要求 resolver，否则安全失败——本地 URI 无法到达 Provider 请求体。

### 5. UI 缩略图与待发送状态（已存在，复验通过）

`ChatScreen.kt` 的 `pendingAttachment: ChatDraftAttachment` 承载发送前缩略图与待发送状态；`ChatImageLoaderTest` 固化加载行为。

## 测试证据

- `gradlew test`（java_development gradle_test）：`BUILD SUCCESSFUL in 13s`，exitCode 0。
- `TEST-com.reversetutor.core.llm.ProductionLlmGenerationRuntimeTest.xml`：`tests="13" skipped="0" failures="0" errors="0"`，含 3 个新用例（anthropic/gemini payload、oversized 安全失败）。
- 全库其余套件（含 `LlmProfilePolicyTest`、`ChatGenerationRepositoryTest`、`BackgroundGenerationRepositoryTest`）随全量构建通过。

## 修改文件清单

修改：
- `core/llm/src/test/java/com/reversetutor/core/llm/ProductionLlmGenerationRuntimeTest.kt`：新增 3 个用例 + `localImageAttachment()` 辅助函数。

无生产代码改动（本任务为补齐测试固化既有实现；实现缺陷未发现——执行期 resolver 架构保证 content:// 不外泄）。

## 冻结路径与敏感信息检查

- 未触碰 `core/model`、`core/protocol`、`core/data/preferences`、SecretStore、Room schema/DAO/migration。
- 测试 URI 均为虚构（`content://private/image`）；断言明确拒绝 `content://`、密钥值、Provider 内部响应出现在请求体；报告不含真实 key/URL/Authorization/原始响应。

## 验收结论

- [x] `qwen3.7-flash` 可进入图片发送流程；未声明 Qwen 模型保持拒绝
- [x] 本地 `content://` URI 不泄露（OpenAI/Anthropic/Gemini 三协议均有测试固化）
- [x] 超大或不支持图片时安全失败（无传输请求、可重试语义正确、UI 安全提示）
- [x] 重启恢复不重复发送（既有恢复幂等测试复验通过）

未 commit / 未 push（等待统一授权）。
