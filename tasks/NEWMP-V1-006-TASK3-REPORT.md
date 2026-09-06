# NEWMP-V1-006 Task 3：本地资料与多模态输入

## 完成内容

- 聊天附件菜单新增“从手机选择资料”，直接调用系统文件选择器。
- 选中的资料复用现有 SourceRepository 解析链路，成功后绑定当前会话并刷新资料快照；失败只显示安全提示。
- OpenAI-compatible 的 `qwen3.7-flash*` profile 被识别为支持图片，其他未明确标记的 Qwen 模型仍保持保守拒绝。
- 增加执行期图片解析器：本地 `content://` 图片读取为有界 base64，再生成 provider payload；不会把手机 URI 原样发送。
- OpenAI-compatible、Anthropic、Gemini 均使用 provider 对应的图片字段。

## 验证

- `:core:llm:testDebugUnitTest --tests "*.LlmProfilePolicyTest" --tests "*.ProductionLlmGenerationRuntimeTest"`：通过。
- `:feature:chat:compileDebugKotlin`、`:app:compileDebugKotlin`：通过。
- 虚拟机 `emulator-5554` 保持启动，宿主应用保持前台；未清理数据、未卸载 APK。

## 尚未完成

- 真机当前未被 ADB 识别，尚未完成华为设备上的文件选择和 Qwen 实际图片请求验证。
- Profile 能力目前按安全规则识别，尚未加入独立可编辑的持久化 `supportsVision` 字段；该项需要单独的数据协议与迁移评估。

未提交、未推送。本报告不包含模型地址、密钥或原始图片内容。
