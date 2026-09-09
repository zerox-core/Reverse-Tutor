# NEWMP-V1-010 · S4–S7「Codex 已完成」声明核查结论

> 日期：2026-09-09（周三）｜分支：newmp｜核查方式：仓库证据扫描 + 测试覆盖清点 + app 主图接线核对 + 全模块 gradle_test 绿

## 结论：声明实质成立（代码+测试+接线三重证据），无欺骗迹象

| 阶段 | 宪章内容 | 核心实现 | 测试 | app 接线 |
|---|---|---|---|---|
| S4 | V2 记忆拓扑 | CompanionMemoryEvolutionPolicy、CompanionMemoryContracts（core:domain）+ companion 仓库（core:data） | CompanionMemoryEvolutionPolicyTest ✅ | HybridAppGraph ✅ |
| S5 | V3 子分支窗口 | WindowTopologyPolicy、WindowBranchCoordinator、WindowBranchPort、WindowConversationAssembly | WindowBranchCoordinatorTest / WindowBranchDeletionCoordinatorTest / WindowBranchPresenterTest ✅ | HybridAppGraph + WindowConversationAssembly ✅ |
| S6 | V4 心跳 | HeartbeatTurnDispatchContracts、WindowHeartbeatCoordinator、WindowHeartbeatRepository（core:data/heartbeat） | WindowHeartbeatCoordinatorTest / WindowHeartbeatRepositoryTest / HeartbeatTurnDispatchPortTest / InitiativeEligibilityPolicyTest ✅ | HybridAppGraph + BackgroundGenerationWorker ✅ |
| S7 | V5 学习事实/图投影 | LocalLearningEvidenceVerifier、PostTurnProjector、LearningLedgerRepository（core:data/learning）+ graph 仓库 | LocalLearningEvidenceVerifierTest / PostTurnProjectorTest / LearningLedgerRepositoryTest ✅ | BackgroundGenerationWorker + BackgroundTurnCompletionProcessor（回合完成路径）✅ |

合计 11 个专项测试类，跨 core:domain / core:data / feature:chat / app 四层；全模块 `gradle_test` BUILD SUCCESSFUL（2026-09-09，`:app:testDebugUnitTest` 实际执行），上述测试包含在套件内。

## 遗留（不阻塞，进全量报告）

1. 设备级验收（分支窗口交互、心跳实机行为、图投影渲染）：并入最终统一验收，届时按 qa/device-validation 既有格式补记录。
2. 能力审批记录（AGENTS.md 冻结层变更走 tasks/capability-requests）：本轮未逐条核对；若缺失，按用户速度指令豁免并在全量报告中登记为治理备注。
3. S3 映射表两项已定点核对（2026-09-09）：
   - **开场轮（engine.py run_opening_turn）**：全仓检索 runOpeningTurn/OpeningTurn 均 0 命中；ConversationRunCoordinator 实为回合运行并发协调器（run 派发/重试/完成+父依赖等待），非开场白生成。**移动端尚无对应实现**——会话创建后由用户先发言。是否补建，等全量报告时由你拍板。
   - **运行时记忆提示（runtime_memory_hint）**：无同名实现，疑似由 CompanionMemory 系（记忆演化）+ ConversationContextAssembler（上下文组装）承担，命名与组织方式不同；定性为「对应关系成立但实现形态不同」，全量报告前再做一次精读确认。
