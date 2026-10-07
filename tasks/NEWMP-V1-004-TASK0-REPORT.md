# NEWMP-V1-004 Task 0：基线与问题登记（2026-09-06，ZCode）

## 基线

- 分支：`newmp` ✓（不合并 main）
- HEAD：`7fe9fcc test(native): finalize v1-003 acceptance evidence` ✓ 含 V1-003 全部产出（processor 全 handle 校验、6 组新测试、11To12 迁移测试、两份报告）
- 工作树：**干净**（无未提交改动，无其他 agent 在途文件；V1-004 改动不会覆盖任何在途工作）

## 冻结路径

`core/model`、`core/protocol`、SecretStore、签名配置、导入导出语义：零触碰；`local.properties` 不读取、不打印、不提交。

## V1-004 允许修改范围（本指令）

- Task 1：`core/domain/SourceGroundedCheckContracts.kt`、`SourceGroundedCheckPolicy.kt`（+其测试）、`core/llm/LlmAssistantReplyEnvelopeParser.kt`/`LlmGenerationLifecycle.kt`（wire 兼容）、`core/data/agent/AgentPayloadCodec.kt`（artifact payload 编解码兼容）、`app/.../SourceGroundedCheckPlanMapper.kt`、`BackgroundTurnCompletionProcessor.kt`（+测试）
- Task 2–4：相应测试文件为主，必要时最小实现补丁
- Task 0/5：本报告与 `NEWMP-V1-004-EXECUTION-REPORT.md`

## 已知环境限制

1. **API 36 迁移限制（RT-2026-031）**：`core:data` instrumentation APK target SDK 23 被 Android 16 平台拒绝安装；`ReverseTutorDatabaseMigration11To12Test` 只能在 Android 12/13 真机或 API≤34 AVD 执行。API 36 仅可做 app 行为测试。
2. 上一阶段唯一 blocker：该迁移测试**已写入仓库、编译通过、未执行**——本阶段 Task 5 需在兼容设备上补跑（若设备仍不可用，继续如实记录 blocked，不得以 JVM 结果冒充）。
3. 共享 AVD（emulator-5556，ZeroXCoreTest/Pixel_8_Pro）存在外部进程清理包体的历史，设备收尾需带"重装宿主+回前台"步骤。

## 本阶段设计要点（Task 1 契约升级）

- `SourceGroundedCheckPlan` 增加显式 `sourceRevisions: Map<handle, revision>`；空 map = 旧单 revision 格式，按 legacy `sourceRevision` 语义兼容。
- 显式格式下每个 `sourceHandles` 成员必须有 map 条目；任一缺失（删除/未映射）或值与实时 revision 不符（漂移/重处理）→ 整体 `Unverified`。
- 验证不再以"首个 handle"为准；旧格式（单 revision）行为不变。
- wire（模型候选 JSON）与 artifact payload（codec）增加可选平行字段，旧 JSON/旧 artifact 读取兼容。

## 预计新增/修改文件

- 修改：domain 契约与策略、llm parser（checkPlan 解析）、data codec、app mapper 与 processor、对应测试
- 新增：仅测试方法，无新源文件（预计）；报告两份
