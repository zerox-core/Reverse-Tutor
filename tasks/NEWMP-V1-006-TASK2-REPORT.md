# NEWMP-V1-006 Task 2 报告：流式输出与临时片段生命周期

日期：2026-09-06 · 执行通道：Cloudflare 本地开发 MCP · 项目：`F:\xw\reverse-tutor-newmp`（分支 `newmp`）

## 1. 现状结论

进入 Task 2 时经逐文件审计，**流式链路主体已在此前提交中实现并通过测试**：

- **Transport**：`UrlConnectionProviderHttpTransport.executeStreaming()` 逐行读取（`reader.forEachLine`），每行回调 `onLine`；`ProductionLlmGenerationRuntime` 在 `request.streaming` 时走流式路径，逐行 `sseData()` 解析并按协议（OpenAI delta.content / Anthropic delta.text、content[].text / Gemini candidates.parts[].text）提取文本片段，逐段调用 `request.onStreamChunk`。
- **Partial store**：`GenerationPartialStore`（core/data）为进程内 `ConcurrentHashMap`，`jobId + generationToken` 双绑定（旧 token `append` 返回 null 不更新）、上限 1200 字符、不写 Room、不写日志。
- **Worker 唯一持久化**：`BackgroundGenerationRepository.runGenerationJob` 将 `onChunk` 喂给 `partialStore.append`；终态（Generated/ProviderFailed/NoModelConfigured/UnsupportedVision/BlankPrompt/Stale）均 `partialStore.clear`；assistant 消息仍仅经 `ChatGenerationRepository.generateReply` 的单一 `saveMessage` 路径写入。
- **UI 消费**：`ChatScreen` 以 250ms 轮询 `getJob` + `getGenerationPreview`，仅当状态为 Queued/Running 时设置 `ChatGenerationUiState.Streaming(preview)` 渲染 `StreamingGenerationRow`；进入终态后清除 token、刷新正式时间线（`reload()` → 从 DB 重载）。进程内 store 随进程死亡自动消失。

**本任务发现并修复的真实缺口（Red→Green）**：`cancelSessionGenerationJobs` 取消会话任务时只改 DB 状态，未清理 `partialStore` 残留片段。

## 2. Red 测试与结果

新增 `cancellingSessionJobsDropsPartialPreviewFragments`（`BackgroundGenerationRepositoryTest.kt`，原 18 测试保留 → 19）：入队任务 → `partialStore.append("正在想")` → `cancelSessionGenerationJobs` 返回 1 → 断言 `getGenerationPreview` 与 `partialStore.get` 均为 null。

Red 运行（`gradle_test` 全量）：`164 tests completed, 1 failed`，`:core:data:testDebugUnitTest` BUILD FAILED（30s）。XML 证据：`tests=19 failures=1`，失败项 `cancellingSessionJobsDropsPartialPreviewFragments`：`java.lang.AssertionError: expected null, but was:<正在想>`。其余 163 个测试全绿。

## 3. Green 修复与结果

`BackgroundGenerationRepository.kt` 的 `cancelSessionGenerationJobs` 循环内新增 3 行（git diff 1,195 字符，仅此一处）：

```kotlin
// NEWMP-V1-006 Task 2: cancellation must not leave stale streaming
// previews behind; drop the process-local fragment immediately.
partialStore.clear(entity.id)
```

顺带说明取消后的迟到 chunk：Worker 侧运行中收到取消后，`runGenerationJob` 的 `isGenerationTokenCurrent` 会因状态不在 ActiveStatuses 判 Stale 并清 store；UI 侧仅当状态 Queued/Running 才显示 Streaming，双重保险。

Green 运行（`gradle_test` 全模块 debug+release）：**BUILD SUCCESSFUL in 1m 18s，exitCode 0**（中途一次 `:feature:chat:compileDebugKotlin` 报 "Failed to clean up output files"——Windows 文件锁瞬时冲突，与代码无关，稍候重试即过）。

定向套件计数（testDebugUnitTest XML，15:50:56）：

| 套件 | tests | failures |
|---|---|---|
| BackgroundGenerationRepositoryTest | 19 | 0 |
| ChatGenerationRepositoryTest | 14 | 0 |
| GenerationPartialStoreTest | 1 | 0 |
| ProductionLlmGenerationRuntimeTest | 10 | 0 |
| LlmGenerationLifecycleTest | 10 | 0 |
| feature:chat 全部 35 套件 | 243 | 0 |

清单点名的既有测试（逐条核对存在且通过）：`streamingRuntimeReportsSseChunksBeforeReturningFinalResult`（chunk 顺序 A→B）、`streamedReplyReportsChunksInOrderButPersistsOneAssistantMessage`（多段回调 + 仅一条 assistant）、`previewIsTokenScopedBoundedAndRemovedOnCompletion`（token 绑定/上限/清理）、`staleGenerationTokenDoesNotPersistAssistantMessage`、`staleTokenAfterRuntimeDoesNotPersistAssistantMessage`、`rerunningCompletedJobDoesNotWriteAssistantAgain`、`recoveryRequeuesRunningJobsAndCancellationPreventsExecution`。

## 4. 修改文件清单

| 文件 | 改动 |
|---|---|
| `mobile-native/core/data/src/main/java/com/reversetutor/core/data/background/BackgroundGenerationRepository.kt` | cancel 路径新增 partialStore.clear（+3 行，git diff 确认无其他变化） |
| `mobile-native/core/data/src/test/java/com/reversetutor/core/data/background/BackgroundGenerationRepositoryTest.kt` | 新增取消清理 Red 测试（18→19） |

## 5. 实际执行命令与真实结果

1. `aily-mcp call read_file`（transport/runtime/repo/store/worker/UI/各测试，约 10 次）→ 审计实现现状。
2. `aily-mcp call write_file`（2 次，测试 + 实现）→ 均回读一致（34,127 / 31,651 bytes）。
3. `aily-mcp call java_development {gradle_test}` Red → `164 tests completed, 1 failed`。
4. 同命令 Green → `BUILD SUCCESSFUL in 1m 18s, exitCode 0`。
5. `aily-mcp call git_diff`（实现文件）→ 仅 +3 行。
6. `aily-mcp call search_content`（feature/chat streaming 关键字）→ UI 消费链确认。

## 6. 设备型号/API/测试步骤

Task 2 为 JVM 单元测试层，无设备操作（设备验证归 Task 7）。

## 7. 验收标准逐条结论

| 验收标准 | 结论 |
|---|---|
| 用户能看到逐段内容 | ✅ UI 250ms 轮询 preview → StreamingGenerationRow（代码审计 + feature:chat 243 测试全绿） |
| 最终只有一条完整 assistant | ✅ 单一 saveMessage 路径 + `streamedReplyReportsChunksInOrderButPersistsOneAssistantMessage` + `rerunningCompletedJobDoesNotWriteAssistantAgain` |
| 退出、重启、失败、重试不产生半截/重复消息 | ✅ 进程内 store 随进程消失；失败/Stale 全路径 clear；取消路径为本任务新增 clear（Red→Green 证据如上）；重试新 token 不更新旧片段 |
| 不新增第二条 assistant 写入路径 | ✅ 零持久化路径改动，仅内存清理 |

## 8. 未完成项、根因与最小判别实验

无阻塞性未完成项。已知边界（非缺陷，供验收知悉）：

- 流式片段经 `toVisibleTimelineText()` 投影后才入 store（`ChatGenerationRepository.onStreamChunk`），Task 1 的过滤与 1200 字符上限对流式预览同样生效。
- SSE 按行读取依赖 `\n` 分隔的 data: 行；未处理跨行续接的 SSE 字段（现网 OpenAI/Anthropic/Gemini 均单行 data）。最小判别实验：构造 `data: {"choices"...` 跨两行的传输，断言当前行为（片段解析失败被丢弃、最终结果仍正确聚合）。

## 9. git diff --check / 冻结路径 / 敏感信息检查

- **git diff --check 等效**：两处改动逐行扫描尾随空白 = 0；git_diff 输出无空白错误标记。
- **冻结路径**：改动仅 `core/data` 允许清单（BackgroundGenerationRepository + 其测试）；未触碰 core/model、core/protocol、core/data/preferences、SecretStore、Room schema/DAO/migration。
- **敏感信息**：新增内容仅含测试假数据（"正在想"、token-preview、job-preview）；无 key/URL/Authorization/原始响应。既有测试 `providerFailureWithSensitiveDiagnosticsMapsToSafePersistedError` 明确断言 sk-/Authorization/https:// 不落库。
