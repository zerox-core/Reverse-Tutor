# NEWMP-V1-003 执行报告（2026-09-05，ZCode）

基线：`newmp` @ `57a45b6`（Task 0 时工作树干净，前序在途全部已入库）。全程未 commit、未 push、未 tag。

## 1. 各 Task 完成状态

| Task | 状态 | 说明 |
|---|---|---|
| 0 盘点 | ✅ 完成 | 报告 `NEWMP-V1-003-TASK0-REPORT.md`；基线盘点无冲突 |
| 1 热更新/版本快照 | ✅ 完成 | 基线已有 revision 机制；本会话修复 **processor 仅校验首个 handle** 的规格偏差 → `currentRevisionForCheck` 校验全部引用 handle 的实时 revision，任一漂移/删除 → `""` → 整体 Unverified 零写入；补 2 个 processor 测试 + SourceRepositoryTest 断言 reprocess 推进 `createdAtEpochMillis`（revision 之根） |
| 2 检查候选生成 | ✅ 完成 | 基线 parser 能力齐备；补 3 测试：四种规则类型全解析、未知规则丢弃且聊天保留、敏感字段（URL/Bearer/sk-/Authorization）计划级拒绝 |
| 3 本地验证与台账 | ✅ 完成 | 基线闭环齐备；补 2 测试：本地验证失败写 failed 证据（correctness=0，绝非 passed/mastery）、无 checkPlan 的普通聊天零学习事实；"自评不能绕过 verifier" 由 projectCheck 的 verifier-owned 字段覆写钉死（既有 sourceCheck 测试断言 received.correctness==verifier 值） |
| 4 重启与 artifact | ⚠️ 代码完成，设备运行 blocked | 新增 `ReverseTutorDatabaseMigration11To12Test.kt`：**编译通过**（`:core:data:compileDebugAndroidTestKotlin` 成功）。**未执行**：当前唯一连接设备为 emulator-5556（API 36），RT-2026-031 判定 core:data instrumentation 在 API 36 被平台拒绝，且任务规则禁止以 API 36 作 Room 迁移证据。artifact 保存/恢复 checkPlan、旧 artifact 兼容、重启后单次投影、重复恢复不重复投影均有 JVM 层实际运行通过（下表） |
| 5 工具最小闭环 | ✅ 完成 | 白名单（SessionToolPolicy + else 兜底）、幂等 receipt、跨 session 拒绝、引用白名单基线已测；补 1 测试：**工具被拒不吞 assistant artifact**（rejected + blocks 完整保留） |
| 6 统一验证 | ✅ 完成 | 见下 |

## 2. 修改文件（本会话，全部在允许范围）

- `app/.../background/BackgroundTurnCompletionProcessor.kt`（currentRevisionForCheck）
- `app/.../background/BackgroundTurnCompletionProcessorTest.kt`（+5 测试）
- `core/llm/.../LlmAssistantReplyEnvelopeTest.kt`（+3 测试）
- `core/data/.../sources/SourceRepositoryTest.kt`（+1 断言）
- `core/data/src/androidTest/.../ReverseTutorDatabaseMigration11To12Test.kt`（新增）
- `tasks/NEWMP-V1-003-TASK0-REPORT.md`、本报告
- 未触碰：core/model、core/protocol、SecretStore、签名、导入导出、local.properties（未读取未打印）、UI/Compose

## 3. 实际测试命令与数量

```
:core:domain:testDebugUnitTest :core:llm:testDebugUnitTest :core:data:testDebugUnitTest :app:testDebugUnitTest :app:lint :app:assembleDebug
→ BUILD SUCCESSFUL（全量）
```

| 套件 | 实际运行通过 |
|---|---|
| core:domain 全量 | **190**，0 失败 |
| core:llm 全量 | **48**，0 失败（含 Envelope 9） |
| core:data 全量 | **159**，0 失败（含 SourceRepository 6） |
| app 全量 | **273**，0 失败（含 Processor 8、Projector 7、Verifier 7、Classifier 13） |
| `git diff --check` | 干净（EOF 空行两处已修） |
| Migration11To12Test | 编译通过 / **未执行**（见状态表） |
| 证据分级 | 上表为实际运行；Migration11To12 为编译+静态；无"源码审读通过"项 |

## 4. 设备

- serial：`emulator-5556`，Pixel_8_Pro AVD，**API 36**（Android 16）。无 Android 12/13 真机或 API≤34 AVD 可用。
- app 行为场景：`Phase2CoreLoopRestartDeviceTest` **实际执行 1 test，通过**（真实 close+launch 重启 ×3 实例、无模型安全失败、assistant 计数持久、重开恢复）。
- 设备覆盖到的任务场景：创建会话/发送/后台生成/自然聊天恢复、退应用重开历史保留、无重复 assistant（18/18 JVM 钉 + 设备重启恢复）；资料整段不入聊天、revision 新旧不串线由 JVM/adapter 测试钉（设备场景不含资料导入步骤——设备为共享 AVD，避免干扰外部项目会话）。
- 收尾：测试包安装记录消失（该共享 AVD 存在外部进程清理包体）；本会话已重装宿主 `app-debug.apk`、`pm enable` 确认 enabled、`am start` Status: ok、`topResumedActivity = com.reversetutor.preview/.MainActivity` **前台核对通过**。

## 5. 失败项与根因

本会话最终 0 失败。过程中 3 次真实失败均修复并记录于状态表（processor 期望 1 次、测试结构 2 次、EOF 空白 2 处、assertTrue import 1 次）。

## 6. Blocker

1 个：**Migration11To12Test 的设备执行**需要 Android 12/13 真机或 API≤34 AVD（建议复用此前华为 BRA-AL00 `9CN0223C27017326`）。接入后单条命令即可：
`:core:data:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.reversetutor.core.data.ReverseTutorDatabaseMigration11To12Test`（注意 RT-2026-007：测后卸载 test 包、宿主 re-enable、回前台）。

## 7. 冻结路径 / 密钥

冻结路径 diff 为空；未读取、未打印、未保存任何 key/URL/Authorization/raw transcript；报告中无设备端敏感输出。

## 8. 后续建议

1. 接入兼容设备执行 11To12 迁移测试并出真机证据。
2. 该共享 AVD 的外部包清理进程会删除宿主，Codex 审查若在此设备跑 instrumentation，收尾应加"重装宿主+回前台"步骤。
3. 多资料 plan 的 `sourceRevision` 字段目前仅代表首个 handle；若未来出现"跨资料合并检查"，考虑把 handle 级 revision 提升到 plan 契约（core:domain 变更，另行评审）。
4. 交由 Codex 统一审查、修复、测试、提交。

## 9. Codex 统一验收补充（2026-09-05）

- 发现并修复 agent 新增测试中的 UTF-8 中文乱码；相关测试改为稳定的 ASCII fixture，避免测试源文件被错误编码。
- 修复后复跑：`:app:testDebugUnitTest`、`:core:data:testDebugUnitTest`、`:core:llm:testDebugUnitTest`、`:core:domain:testDebugUnitTest`，全部 BUILD SUCCESSFUL。
- `:core:data:compileDebugAndroidTestKotlin` BUILD SUCCESSFUL；`ReverseTutorDatabaseMigration11To12Test` 仍未在 API 36 上执行，未伪造迁移设备证据。
- `:app:lint` 与 `:app:assembleDebug` BUILD SUCCESSFUL。
- `git diff --check` 干净；冻结路径仍无改动；本次没有读取或修改任何密钥配置。
