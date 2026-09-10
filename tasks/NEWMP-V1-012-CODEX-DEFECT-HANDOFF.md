# NEWMP-V1-012 缺陷交接清单（移交 Codex 处理）

> 来源：2026-09-10 用户指令「把这个无法修复的 bug 列入清单我后续交给 codex 完成」。
> 当前仅 1 条。后续移交条目追加在本文件，编号 D-2、D-3……

---

## D-1 真流式增量渲染无法实现：SSE 被缓冲为结尾一次性下发

**状态**：应用客户端侧已排除全部嫌疑、不可修复；移交 Codex 定位服务端/中转层。

### 现象

- 用户报告（2026-09-09）：「流式输出还是全部生成后短暂弹出一个流式窗口全量输出，没有真正的一边生成一边输出」。
- 实机观测（2026-09-10，emulator-5554，HEAD=c9a9d37）：连续 UI 采样约 2 秒粒度，生成全程仅显示「正在生成回复」占位，随后一帧之内直接出现完整回复全文，任何采样点均未出现部分文本。

### 已排除的客户端嫌疑（全部为实查代码，含行号）

- **流式开关**：`LlmGenerationLifecycle.kt` 默认 `streaming: Boolean = true`，全工程无覆写点；请求 payload 携带 `"stream": true`。
- **传输层**：`UrlConnectionProviderHttpTransport.kt` `executeStreaming()` 使用 `reader.forEachLine { onLine(line) }` 逐行真投递；请求头已设 `Accept: text/event-stream` 与 `Accept-Encoding: identity`（防 HttpURLConnection 透明 gzip 整段缓冲）。
- **SSE 解析**：`ProductionLlmGenerationRuntime.kt` `openAiText()` 兼容 `choices[0].delta.content`（流式 chunk）与 `choices[0].message.content`（非流式）两种形态；`sseData()` 正确剥 `data:` 前缀并过滤 `[DONE]`。
- **回调接线**：`LlmGenerationLifecycle.kt:506` 将 `onStreamChunk` 接入 request；runtime 第 88 行逐 chunk 回调；错误路径（`runCatching{}.getOrNull()`）失败即 Failure，**无静默降级为非流式**。
- **预览/持久化管线**：`BackgroundGenerationRepository` 的 `onChunk → GenerationPartialStore.append(jobId, token, chunk)` 已接线；`ChatScreen.kt` 250ms 轮询 `getGenerationPreview` 曾存在（见下方缓解措施）。

### 根因推断

SSE chunk 在连接结尾一次性到达 → 客户端 250ms 轮询只能捕到一帧全量文本 → 产生「全部生成后短暂弹出流式窗口全量输出」的精确症状。用户确认同一 API 在其他 agent 中流式正常，故嫌疑集中在：服务端/中转层对本客户端请求路径的缓冲（relay 常见 SSE 缓冲），或模型/路由差异。

### 已应用的缓解（用户拍板 2026-09-10）

设计决策：后台生成场景不做流式提醒，只在全量发布后提醒一次。已删除 `feature/chat/.../ChatScreen.kt` 轮询循环中唯一的 `ChatGenerationUiState.Streaming(preview)` 生产赋值点——生成全程只显示 Pending 指示器，终态分支一次性发布全量回复并触发完成通知。`StreamingGenerationRow`、`GenerationPartialStore`、`onChunk` 数据管线**完整保留未删**，本缺陷修复后可直接恢复。

实机验证（本缓解实施后）：构建 BUILD SUCCESSFUL，装机后发消息连拍三张（+2.5s/+5.5s/+8.5s）全程无任何流式部分文本弹出；首条测试消息全量回复正常发布。

### 移交 Codex 的建议排查路径

1. 在 `executeStreaming` 的 `onLine` 回调临时加 logcat 打点（行到达时间戳+间隔），实测 chunk 到达分布：一次性 vs 渐进。
2. 用与 app 完全相同的 baseUrl/model/headers 在本机 `curl -N` 直接测 SSE 分块时序（对照其他流式正常的 agent 的请求形态）。
3. 核对 app 实际配置的端点形态：百炼 compatible-mode 直连 vs 中转/relay；中转层是 SSE 缓冲的高发位置。
4. 若确认为 relay 缓冲：评估关闭中转或调整其 SSE 透传/缓冲配置。
5. **修复后恢复流式显示的回滚点**：把 `ChatScreen.kt` 轮询循环中的预览分支从 git 历史恢复即可（数据管线未动）。

### 备注

- 2026-09-10 实测时第二条测试消息发出后 8.5 秒内未见回复（消息列表不自动滚动、指示器在折叠线下方），该轮生成是否完成未最终确认；若复测时发现已完成属正常。
