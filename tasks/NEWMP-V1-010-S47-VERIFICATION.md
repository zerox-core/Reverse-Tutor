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
2. 能力审批记录（2026-09-09 已核对）：tasks/capability-requests 共 6 份申请（P3×3 + P6×3），覆盖 S4–S7 冻结层变更方向；但「P6-window-topology-memory-and-heartbeat.md」状态仍为「proposed，未批准，不得实施」，§5 审批门禁三项均未勾选，而实际 Room 已至 version 12、migration 6→9（该申请规划内容）及 9→12 全部落地并提交。**冻结层实现在无批准记录下落地**——按用户速度指令豁免执行，登记为全量报告治理发现 G1，待统一验收时由用户追溯确认。
3. S3 映射表两项已定点核对（2026-09-09）：
   - **开场轮（engine.py run_opening_turn）**：全仓检索 runOpeningTurn/OpeningTurn 均 0 命中；ConversationRunCoordinator 实为回合运行并发协调器，非开场白生成。但定点读取发现移动端另有对应物：NewSession 流程内置「开场消息」字段（NewSessionLifecycle 默认「准备好后，请开始讲给我听吧。」；预设流为角色自述+剧情；新会话界面提供可编辑文本框），会话创建即保存为首条 Assistant 消息。**结论 = 对应物存在但实现形态不同（确定性模板 vs engine.py LLM 生成）**，属产品设计差异而非缺失；是否再补 LLM 生成开场，等全量报告时由你拍板。（本条更正早先「尚无对应实现」判断：初查仅检索 runOpeningTurn/OpeningTurn 命名，漏掉 openingMessage 机制。）
   - **运行时记忆提示（runtime_memory_hint）**：精读确认（2026-09-09）。无同名实现；ConversationContextAssembler 以有界、确定性、逐源失败降级的方式聚合六类上下文（近况消息/记忆引用/历史错误/图谱前置缺口/待复习知识点/来源证据），对应 engine.py 的 runtime_memory_hint + kg_context + error_logs + due_reviews 分散注入。**定性：对应关系成立且组织更聚合（已确认，不再待核）。**
