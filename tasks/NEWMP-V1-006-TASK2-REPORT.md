# NEWMP-V1-006 Task 2：流式输出执行报告

## 完成内容

- 为 `LlmGenerationRequest` 增加可选的进程内 chunk 回调，旧调用点默认不变。
- Fake runtime 按原始 chunk 顺序回调；真实 HTTP transport 增加逐行 SSE 读取，OpenAI-compatible、Anthropic、Gemini 复用现有解析逻辑。
- `ChatGenerationRepository` 将 chunk 交给临时预览回调，并继续只保存一条最终 assistant 消息。
- 新增有界、按 `jobId + generationToken` 绑定的 `GenerationPartialStore`。它不写 Room、不写日志；完成、失败、过期或 token 失效时清理。
- 聊天页在任务运行期间显示临时“正在输入”片段，任务结束后由正式消息刷新替换；重开会话不会恢复半截临时文本。

## 验证结果

- `:core:llm:testDebugUnitTest --tests "*.ProductionLlmGenerationRuntimeTest"`：通过。
- `:core:data:testDebugUnitTest --tests "*.ChatGenerationRepositoryTest" --tests "*.GenerationPartialStoreTest"`：通过。
- `:feature:chat:testDebugUnitTest`：通过。
- 受影响模块编译及 `:app:assembleDebug`：`BUILD SUCCESSFUL`。
- 专用虚拟机 `emulator-5554` 已启动，Debug APK 已覆盖安装并启动到 `MainActivity`；未清理应用数据、未卸载应用。

## 边界与后续

- 当前临时片段是进程内状态：应用进程被杀后会安全丢弃，正式 assistant 消息仍由原 Worker 路径恢复。
- 真机当前未被 ADB 识别，尚未在 Huawei BRA-AL00 上进行本轮安装验证。
- 下一阶段为 Task 3：聊天内手机文件入口与显式多模态能力配置；之后再做后台状态热更新和通知的人工流程验收。

未提交、未推送。本报告不包含模型地址、密钥或原始 Provider 内容。
