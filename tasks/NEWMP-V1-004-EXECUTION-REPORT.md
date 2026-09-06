# NEWMP-V1-004 执行报告（2026-09-05，ZCode）

基线：`newmp` @ `7fe9fcc`（Task 0 确认，工作树当时干净）。全程未 commit、未 push、未 tag。

## 1. 各 Task 完成状态

| Task | 状态 |
|---|---|
| 0 基线与登记 | ✅ `NEWMP-V1-004-TASK0-REPORT.md` 已交付 |
| 1 资料 revision 契约升级 | ✅ 六层贯通（domain 契约/策略 → llm wire/parser → data codec → app mapper/verifier/processor），9 项新测试实际运行通过 |
| 2 资料导入与热更新闭环 | ✅ 必需 6 项测试全部有钉（详见 §3 对应表），本轮无生产代码改动需要 |
| 3 真实结构化回复闭环 | ✅ 必需 6 项测试全部有钉；本轮契约升级强化"多 source plan 逐 handle 校验"为显式 map |
| 4 工具调用与自然聊天 | ✅ 必需 6 项基线已有 + V1-003 补强，逐项核对通过，零改动 |
| 5 统一验证 | ✅ JVM 全量 + lint + assemble 实际运行通过；设备 app 行为 1/1 通过；**migration instrumentation 环境阻塞（有实测证据，未伪装执行）** |

## 2. 契约升级设计（Task 1）

`SourceGroundedCheckPlan.sourceRevisions: Map<handle, revision>`（空 map = 旧单 revision 语义，`revisionFor(handle)` 回落到 `sourceRevision`）：

- `normalize`：显式 map 必须**全覆盖** sourceHandles（缺失/空白 → 整体拒绝）；
- `validateAnswer`：改为对**每个** handle 逐一比对 live revision；任一 handle 在 live map 缺失（删除/不可读）或不等（漂移/重处理）→ 整体 `Unverified`——不存在"只检查第一个"的路径；
- legacy 单值重载保留（全部 handle 按 primary revision 校验），旧 JSON/旧 artifact 兼容；
- wire（`LlmSourceGroundedCheckPlan.sourceRevisions` + parser `"sourceRevisions"` 对象字段）与 artifact codec（第 9 段 payload，`getOrNull(8)` 缺省=legacy）同步升级。

## 3. 必需测试逐项对应（全部实际运行通过）

**Task 1**：单资料旧格式兼容（`legacySingleRevisionPlanStillValidatesEveryHandle…` + 既有 legacy 11）；多资料不同 revision 通过（`multiSourcePlanWithDistinctRevisions…`）；任一漂移整体不通过（policy `anyDrifted…` + processor 端到端 `explicitMapPlanProjectsOnlyWhenEveryHandleIsCurrent` 二段断言）；任一删除整体不通过（`deletedSource…`）；新导入不影响旧任务（`queuedJobKeepsEnqueueTimeSourceSnapshot…` 既有 + 端到端 drift 用例）；旧任务不读新资料正文（artifact/codec roundtrip + candidate 仅来自 envelope/artifact blocks）。

**Task 2**：import→context（SourceContextPortAdapterTest 既有）；reprocess→新 revision（`reprocessUsesExisting…` + V1-003 的 createdAt=200 断言）；queued 快照隔离（BackgroundGenerationRepositoryTest 既有 18）；artifact 保留旧 handle（`artifactRoundTripsExplicitRevisionMap` 新增）；新回合见新 handle（adapter reprocessed 用例）；不重复 assistant（`rerunningCompletedJob…` 既有）。

**Task 3**：四规则解析（`allFourCheckRuleKinds…` 既有）；未知 rule 丢弃留聊天（既有 + 新 `parserHandlesExplicitRevisionMapAndPartialMapFailsClosed` 半覆盖路径）；敏感字段拒绝（既有）；自评 passed + 本地 failed → 最终 failed（`verifiedFailureProjectsFailedReceiptNotMastery` 既有，其 structuredOutcome 注入 passed/1.0f 被覆写）；自评 + 无 verifier → 零台账（`modelSelfAssessmentNeverBecomesLearningFact`/legacy project 既有）；多 source 逐 handle（新端到端用例）。

**Task 4**：合法文档/表格、重复调用幂等、非法工具拒绝、跨 session 拒绝、工具失败保 assistant——基线 + V1-003 六项全在，运行通过。

## 4. 实际测试命令与数量（实际运行）

```
:core:domain:testDebugUnitTest :core:llm:testDebugUnitTest :core:data:testDebugUnitTest :app:testDebugUnitTest :app:lint :app:assembleDebug
→ BUILD SUCCESSFUL（2m10s）
core:domain 195 | core:llm 49 | core:data 160 | app 274 = 678 tests，0 failures，0 errors
:app:connectedDebugAndroidTest (Phase2CoreLoopRestartDeviceTest) → 1/1 通过
git diff --check → rc=0（仅 CRLF 转换提示）
```

## 5. 设备

- emulator-5556，Pixel_8_Pro AVD，**API 36**。
- app 行为：重启恢复场景 1/1 通过（close+launch 真重启，非 recreate）。
- `:core:data:connectedDebugAndroidTest` Migration11To12Test：**实际尝试后环境阻塞**——`INSTALL_FAILED_DEPRECATED_SDK_VERSION: target at least 24, found 23`（RT-2026-031 复现），执行数 0。
- 收尾：test 包已不存在（DELETE_FAILED=已消失）；宿主被共享 AVD 外部进程再次移除 → 已重装 `app-debug.apk`、`am start Status: ok`、**topResumedActivity = com.reversetutor.preview/.MainActivity** 前台核对通过。

## 6. 证据分级声明

- 实际运行通过：§4 全部 JVM 套件、app 行为设备场景。
- 编译通过 + 静态：Migration11To12Test（代码入仓、assembleDebugAndroidTest 成功；设备执行环境阻塞，0 执行，未记为通过）。
- 未执行：`:feature:chat:testDebugUnitTest` 单列（已包含在 :app 依赖构建图但本轮未单独点名运行——实际全量 `test` gate 未跑 release 变体；下轮 Codex 验收可补）。
- 无"源码审读通过"类结论。

## 7. 失败与修复记录（过程中真实发生）

1. 多次 perl 补丁因 CRLF/LF 混合行尾损坏目标文件（lifecycle/parser/policy test/codec）——全部经 `git checkout HEAD -- <file>` 还原后以整行模式重做；最终逐文件 diff 已人工核对为纯增量。
2. `multiSource…` 首跑失败：normalize 未透传 `sourceRevisions` → 修复 return 后通过。
3. verifier 测试旧签名 → 批量转 `associateWith` map。

## 8. 冻结与敏感

`core/model`/`core/protocol`/SecretStore/签名/导入导出：diff 全空。未读取 local.properties；测试数据仅虚构 token（`rev-*`、`sk-abcdef123456` 为拒绝用例的假 secret）；工作树无 APK/DB/日志/截图（构建产物均在 gitignored build/ 下）。

## 9. Blocker

仅一项（继承 V1-003）：**Migration11To12Test 需 Android 12/13 真机或 API≤34 AVD 执行**。接入命令：
`:core:data:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.reversetutor.core.data.ReverseTutorDatabaseMigration11To12Test`（完成后按 V1-003 收尾规程卸载 test 包并恢复宿主前台）。

## 10. 后续建议

1. Codex 验收时补 `test` 全任务（release 变体单测）与兼容设备 migration 实跑。
2. `LlmSourceGroundedCheckPlan.sourceRevision`（primary）与 `sourceRevisions` 双轨并存是为旧 wire/旧 artifact 兼容；若模型侧协议未来收紧，可评审让显式 map 成为唯一真源。
3. 共享 AVD 的外部清包进程持续存在，设备验收窗口建议固定专用 AVD。

## 11. Codex 统一验收补充（2026-09-06）

- 复跑 `:core:domain:testDebugUnitTest`、`:core:llm:testDebugUnitTest`、`:core:data:testDebugUnitTest`、`:app:testDebugUnitTest`，均 BUILD SUCCESSFUL；本次新增的显式 revision 敏感字段拒绝测试通过。
- 复跑 `:app:lint` 与 `:app:assembleDebug`，均 BUILD SUCCESSFUL。
- 修复一个防御性缺口：领域层直接构造的 `sourceRevisions` 映射此前未进入敏感字段扫描；现在 handle 与绑定 revision 均纳入 URL、Authorization、Bearer、secret-like 检查。
- `git diff --check` 通过；`core/model`、`core/protocol`、SecretStore、签名和 `local.properties` 无改动或读取。
- API 36 虚拟机只保留既有 app 行为证据；Room 11→12 迁移仍须在 Android 12/13 真机或 API≤34 AVD 执行。

—— 未提交、未推送，交 Codex 统一审查。
