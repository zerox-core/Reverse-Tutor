# NEWMP-V1-006 Task 3 报告：聊天内资料导入与会话绑定

## 结论

Task 3 完成。聊天内「从手机选择资料」导入链已按 checklist 要求补齐契约测试并修复真实缺陷：快照读取失败时旧实现静默丢弃导入结果（不写入会话、不提示用户），Red 测试精确捕获该缺陷后以最小改动修复，全库 JVM 测试通过（BUILD SUCCESSFUL）。

## Red 阶段证据

- 新增 `app/src/test/java/com/reversetutor/preview/shell/ChatSourcePickImportMapperTest.kt`（4 用例）。
- 第三次全量 `gradlew test` 返回 `279 tests completed, 1 failed`。
- 失败证据（`app/build/test-results/testDebugUnitTest/TEST-com.reversetutor.preview.shell.ChatSourcePickImportMapperTest.xml`）：`tests="4" skipped="0" failures="1" errors="0"`，失败用例 `missingSnapshotSurfacesFailureNoticeInsteadOfSilentDrop`，消息 `java.lang.AssertionError: snapshot read failure must not be silently dropped`。
- 该失败对应提取前 AppShell 分支的真实缺陷：`loadSessionSnapshot` 返回 null 时旧代码 `if (snapshot != null)` 跳过且不设置任何用户可见提示。

## Green 修复（最小改动）

`ChatSourcePickImportMapper.kt` 中：

```kotlin
// 修复前
val snapshot = currentSnapshot ?: return null
// 修复后
val snapshot = currentSnapshot
    ?: return ChatSourcePickImportOutcome.Rejected("资料未能加入当前会话，请重试。")
```

快照读取失败不再静默返回 null，而是返回带重试文案的 `Rejected`，由 AppShell 经 `pendingChatAttachmentNotice` 呈现安全提示。

## Green 阶段证据

- `gradlew test`（java_development gradle_test）返回 `BUILD SUCCESSFUL in 12s`，exitCode 0；`:app:testDebugUnitTest` 真实重跑（非 UP-TO-DATE）。
- `TEST-com.reversetutor.preview.shell.ChatSourcePickImportMapperTest.xml`：`tests="4" skipped="0" failures="0" errors="0"`，含 `missingSnapshotSurfacesFailureNoticeInsteadOfSilentDrop` 通过。
- `TEST-com.reversetutor.feature.chat.ChatAttachmentSheetContractsTest.xml`：`tests="4" failures="0"`——入口顺序含「从手机选择资料」、条目不泄露路径与内部标识、相机权限副标题仅映射拒绝态、副标题只由拍照条目携带。
- `TEST-com.reversetutor.preview.wiring.session.BackgroundTurnPreparationCoordinatorTest.xml`：`tests="7" failures="0"`，含新增 `queued_turns_keep_snapshotted_source_revision_while_later_turns_bind_new_one`——已排队回合持有入队时快照的 `source:src-1:rev-src-1-100`，后续回合绑定 `rev-src-1-900`。

## 修改文件清单

新增（未跟踪）：
- `app/src/main/java/com/reversetutor/preview/shell/ChatSourcePickImportMapper.kt`（纯投影 + 数据类）
- `app/src/test/java/com/reversetutor/preview/shell/ChatSourcePickImportMapperTest.kt`（4 用例）
- `feature/chat/src/test/java/com/reversetutor/feature/chat/ChatAttachmentSheetContractsTest.kt`（4 用例）

修改：
- `feature/chat/src/main/java/com/reversetutor/feature/chat/ReverseTeachingChatScreen.kt`：附件面板条目抽为纯函数 `chatAttachmentSheetActionSpecs` / `chatCameraPermissionSubtitle`（供 JVM 契约测试），渲染行为不变。
- `app/src/main/java/com/reversetutor/preview/shell/AppShell.kt`：`chatSourcePickerActive` 分支改走 `mapChatSourcePickImport`，Bound 时保存快照 + 刷新设置键 + 安全 notice；Rejected 时仅设置 notice。
- `app/src/test/.../BackgroundTurnPreparationCoordinatorTest.kt`：追加 revision 隔离测试。

## Source revision 隔离审计结论（checklist 要求项）

代码审计确认隔离链天然成立并由测试固化：`SourceContextPortAdapter` 以 `sourceRevision = "rev-${id}-${createdAtEpochMillis}"` newest-first 读取 → `SessionPolicyInputMapper.toLlmContextEvidence` 将 revision 嵌入 evidence id（`source:${id}:${revision}`）→ `BackgroundTurnPreparationCoordinator` 入队时 `assembleContext` 一次快照 → `BackgroundGenerationInput.contextEvidence` 持久化。已排队回合锁定旧 revision，后续回合绑定新 revision；测试 `queued_turns_keep_snapshotted_source_revision_while_later_turns_bind_new_one` 固化该契约。

## 冻结路径与敏感信息检查

- 未触碰 `core/model`、`core/protocol`、`core/data/preferences`、SecretStore、Room schema/DAO/migration。
- mapper 与 notice 文案均为通用安全文案，不含本地路径、URL、key、Authorization、资料正文或 Provider 原始响应；测试断言附件面板条目不泄露路径与内部标识。

## 验收结论

- [x] 聊天内资料导入绑定当前会话（Bound 路径写 sourceSelections 去重追加）
- [x] 不可用导入以安全文案拒绝并可重试
- [x] 快照读取失败不再静默丢弃（Red→Green 修复）
- [x] 已排队回合与新回合的 source revision 隔离经测试固化
- [x] 附件面板契约（入口顺序/无泄露/权限副标题）经测试固化
- [x] 全量 JVM 回归通过

未 commit / 未 push（等待统一授权）。
