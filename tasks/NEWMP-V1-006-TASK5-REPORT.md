# NEWMP-V1-006 Task 5 报告：退出聊天后的状态热更新

## 结论

Task 5 完成。首页会话卡片此前不反映后台生成任务（`RepositorySessionHomePortAdapter.loadSessionCards` 只显示最新消息摘要），本任务以 Red→Green 方式补齐：新增 feature/chat 纯映射 `sessionCardGenerationLabel` / `withActiveGenerationJob`，并把 `findActiveJobForSession` 接入首页装配。首页与聊天页现在消费同一持久化任务真相，全量 JVM 测试通过（BUILD SUCCESSFUL）。

## Red 阶段证据

- 新增 `feature/chat/src/test/java/com/reversetutor/feature/chat/SessionCardGenerationTest.kt`（3 用例）。
- 运行 `gradlew test` 失败：`SessionCardGenerationTest.kt:20:18 / :24:18 / :34:18 Unresolved reference: withActiveGenerationJob`——首页卡片生成状态映射不存在，Red 精确落在缺口上。

## Green 实现（最小改动）

新增 `feature/chat/src/main/java/com/reversetutor/feature/chat/SessionCardGeneration.kt`：
- `sessionCardGenerationLabel(activeJob)`：Queued/Running →「生成中」；Failed → 复用聊天页同一安全映射 `backgroundGenerationUiState`（未知错误一律降级为固定文案「生成失败：后台生成失败」）；Completed/Cancelled/Discarded/null → null（回落最新消息摘要）。
- `SessionListItem.withActiveGenerationJob(activeJob)`：仅覆盖 statusLabel，不用聊天文本猜状态。

接线：
- `RepositorySessionHomePortAdapter`（app）新增构造参数 `findActiveJobForSession`（默认 `{ null }`），`statusLabel = sessionCardGenerationLabel(findActiveJobForSession(session.id)) ?: latestSummary`。
- `HybridAppGraph`：`createFrontendFactories` 新增参数并由调用点传入 `backgroundGenerationRepository::findActiveJobForSession`，装配进 sessionHomePort。

## Green 阶段证据

- `gradlew test`：`BUILD SUCCESSFUL in 19s`，exitCode 0。
- `TEST-com.reversetutor.feature.chat.SessionCardGenerationTest.xml`：`tests="3" skipped="0" failures="0" errors="0"`——Queued/Running 显示「生成中」；Completed/Cancelled/null 保持最新消息摘要；Failed 未知错误不泄露（含 URL/key/异常类名的持久化错误被降级为通用文案）。

## checklist 逐项核对

- Queued/Running 显示"生成中"；Completed 显示最新消息；Failed 显示安全失败状态：**新增测试固化**（首页卡片层）+ 既有 `ChatUiStateTest`（聊天页层，含 `backgroundGenerationUiState` 对 Queued/Running/Failed 的安全映射断言）。
- 离开聊天后重进，恢复原 jobId/token，不创建第二个任务：**既有实现复验**——`ChatScreen.kt` 重进时 `findActiveJobForSession` 只读恢复 `activeBackgroundJobId/activeGenerationToken` 并接续轮询；repo 级 `BackgroundGenerationRepositoryTest.reopeningSessionFindsItsNewestActiveBackgroundJob` 固化。
- 同一会话多个历史任务只显示最新仍活动任务：`findActiveJobForSession` 取 `asReversed().firstOrNull { active }`，上述测试已覆盖（job-old Completed、job-new Running → 返回 job-new）。
- 使用已有 findActiveJobForSession 和持久化状态，不用聊天文本猜状态：卡片映射输入只有任务状态与白名单错误文案。
- 首页和聊天页使用同一安全状态映射：Failed 路径直接复用 `backgroundGenerationUiState`；生成中/回落语义与聊天页一致。
- 完成后刷新时间线和会话摘要：任务完成后卡片回落最新消息摘要（loadSessionCards 每次进入首页重载）；聊天页轮询循环在完成时刷新时间线（既有实现）。

## 修改文件清单

新增：
- `feature/chat/src/main/java/com/reversetutor/feature/chat/SessionCardGeneration.kt`
- `feature/chat/src/test/java/com/reversetutor/feature/chat/SessionCardGenerationTest.kt`

修改：
- `app/src/main/java/com/reversetutor/preview/wiring/HybridFrontendPortAdapters.kt`（构造参数 + statusLabel 映射 + 导入）
- `app/src/main/java/com/reversetutor/preview/wiring/HybridAppGraph.kt`（参数透传 + 装配 + 导入）

## 冻结路径与敏感信息检查

- 未触碰 `core/model`、`core/protocol`、`core/data/preferences`、SecretStore、Room schema/DAO/migration。
- 失败文案走既有白名单降级，测试断言含 URL/key/异常类名的持久化错误不出现在卡片；报告无敏感信息。

## 验收结论

- [x] 返回首页能看到当前会话正在生成（卡片 statusLabel=「生成中」）
- [x] 重进会话继续观察同一任务（只读恢复 jobId/token）
- [x] 同一会话只显示最新仍活动任务
- [x] 完成后回落最新消息摘要，失败显示安全文案
- [x] 全量 JVM 回归通过

未 commit / 未 push（等待统一授权）。
