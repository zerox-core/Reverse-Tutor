# NEWMP-V1-003 Task 0 盘点报告（2026-09-05，ZCode）

## 基线确认

- 分支：`newmp`（未合并 main）✓
- 基线提交：`57a45b6 feat(native): complete source-grounded learning loop` ✓（与指令一致）
- 上一提交链：`df0a1c3`（引导学习管线）→ `57a45b6`（资料驱动闭环 + 全部 V1-002/003 前序在途改动已入库）
- 工作树状态：**干净（无未提交改动）**。前几轮全部在途文件已由 57a45b6 吸收，无其他 agent 未落盘改动，不存在覆盖风险面。

## 冻结路径检查

- `git diff --name-only -- core/model core/protocol`：空（工作树干净，基线内含前轮已批准改动）。
- `SecretStore.kt`、签名配置、导入导出语义：本任务不触碰。
- `local.properties`：不读取、不打印、不提交。

## 基线能力现状（对照 V1-003 Task 1–5）

| 能力 | 基线状态 |
|---|---|
| sourceRevision=`rev-<id>-<createdAt>`、证据 ID `source:<id>:<rev>`、旧 job 快照不漂移 | ✅（adapter/SessionPolicyInputMapper + SourceContextPortAdapterTest、BackgroundGenerationRepositoryTest 18/18） |
| reprocessSource 复用 importSource + 新时间戳 ⇒ 新 revision | ✅（SourceRepository.kt:69；SourceRepositoryTest 存在） |
| 多资料 plan 全 handle 白名单校验（normalize 层，用 job 快照） | ✅ SourceGroundedCheckPolicy |
| **验证时按“当前资料”核对全部 handle 的 revision** | ⚠️ 缺口：processor 只对 `sourceHandles.first()` 查当前 revision，第二个 handle 漂移不会被判 Unverified → Task 1 修复 |
| checkPlan 受限解析（LlmSourceGroundedCheckPlan/LlmSourceCheckRule 白名单、限长、fail-closed、普通文本兼容） | ✅（LlmAssistantReplyEnvelopeTest） |
| 本地验证闭环 projectCheck（exact/numeric/required→判定，rubric/漂移→Unverified，共享幂等集） | ✅（LocalLearningEvidenceVerifierTest 7、BackgroundTurnCompletionProcessorTest 存在） |
| artifact 保存/恢复 checkPlan（checkPlanPayload 列 + codec）| ✅（AssistantReplyArtifactRepositoryTest） |
| Room schema 12.json 已导出，非 destructive | ✅ DatabaseSchema/schemas；⚠️ **缺 11→12 migration 设备测试** → Task 4 补 |
| 工具白名单/幂等 receipt/session 绑定 | ✅（SessionTool* 体系） |

## 本任务允许修改的文件（计划）

- Task 1：`app/.../BackgroundTurnCompletionProcessor.kt`（全部 handle revision 校验）+ `BackgroundTurnCompletionProcessorTest.kt`；`SourceRepositoryTest`（reprocess 新 revision 断言，如缺）
- Task 2–3、5：以验证运行为主，仅在测试缺口处补 `core/llm`/`app` 测试
- Task 4：新增 `core/data/src/androidTest/.../ReverseTutorDatabaseMigration11To12Test.kt`（仿 10To11）
- Task 0/6：本报告与执行报告、设备流程
- 全程不触碰：core/model、core/protocol、SecretStore、签名、导入导出语义

## 结论

无角色语义冲突、无冻结边界冲突、无在途覆盖冲突。唯一发现的规格偏差（多 handle revision 验证不全）与测试缺口（11To12 migration）分别归入 Task 1 / Task 4 处理，无 blocker。
