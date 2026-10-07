# NEWMP-V1-016 到点提醒复习（gap 7，2026-09-11）

## 用户指令

任务评论 7684189500746009807："进行"——开工开发队列中的"到点提醒复习"。

## 老版行为（engine.py 实读取证）

每个 study 回合 `run_turn`：`due_reviews = db.list_due_reviews(sess, now)`（next_review_at <= now 的知识点）；有到期时：
- 系统提示追加 `# 到期复习软提示` 块：到期清单（知识点/到期时间/间隔/上次证据）+"软交织提示，不强制打断当前推进；是否带回视用户回复决定；回复合适时用『顺带把【旧知识点】带回来想一下』轻量带回"；
- process_summary 追加"系统检测到 N 个旧知识点到期"；
- 用户说"不想复习/先不管/跳过/以后再说"时 `mark_review_pending` 挂起这些点。

## 现状与缺口

- 掌握度读模型 V1-013 已就绪：`MasteryLedgerProjection.projectDue(facts, now)`（纯函数、已测），复习梯子 [1,3,7,14]d 锚定 fact.occurredAt；
- 写入端已就绪：BackgroundGenerationWorker 每回合经 PostTurnProjector 追加 LearningFactReceipt；
- 缺口：projectDue 生产无调用点；`pendingReviewKnowledgePoints` 只来自图谱 NeedsReview 节点；且映射器 `toLlmContextEvidence` 的 6 条上限被消息证据先占满，排在尾部的 Review 证据永远挤不进提示词。

## 改动（全部非冻结层：app wiring，无需审批）

`app/.../wiring/session/ConversationContextPortAdapters.kt`
- `GraphContextPortAdapter` 构造新增 `learningLedgerRepository: LearningLedgerRepository? = null` 与 `nowEpochMillis: () -> Long = System::currentTimeMillis`
- `listPendingReviewPoints`：先 `MasteryLedgerProjection(snapshotLimit=50).projectDue(台账, now)`（到期优先、最早到期在前），再合并图谱 NeedsReview 标签，trim/去空/去重/limit。snapshotLimit 取 50 而非默认 10，避免低分但到期的知识点在按分数排序的截断中饿死；调用方仍以 reviewLimit=5 收口

`app/.../wiring/session/SessionConversationAssembly.kt`
- 生产接线传入台账仓库与时钟（HybridAppGraph 已传 learningLedgerRepository，实测非空）

`app/.../wiring/session/SessionPolicyInputMapper.kt`
- `toLlmContextEvidence` 重排：知识缺口 + 待复习两块聚合证据移到最前（对齐老版"注入系统提示、必达"的语义，同时修掉被消息证据挤掉的饿死缺陷）；逐条证据顺序不变
- Review 证据 body 追加老版软提示原文要义：`（到期复习软提示：不强制打断当前推进，是否带回视用户回复决定）`——学生人格看到后知道不打断当前推进、看老师回复决定是否轻量带回
- 新增 `DueReviewSoftHint` 常量

## 验证（修必带验）

- 新增 `GraphContextPortAdapterTest`（4 测试）：到期点先出且与图谱标签合并去重并尊重 limit、未到期排除、null 台账保持仅图谱行为、跨空间台账不泄漏
- `SessionPolicyInputMapperTest` 9/9：顺序断言更新（聚合在前）+ 新测试"10 条消息下 Review 证据仍在 6 条上限内且 body 带软提示"
- 全工程 `gradle test` BUILD SUCCESSFUL（exitCode 0）
- 生产文件逐处回读确认（import/构造/到期计算/装配接线/映射器重排/软提示常量）

## 刻意不做（记录在案）

- `mark_review_pending` 挂起（用户说"不想复习"后不再骚扰）：需要新持久化，本期不做；到期点在下一笔该知识点学习事实写入后自然按新梯子顺延
- process_summary 追加"系统检测到 N 个到期"：策略层元数据，不迁移
- 本地推送通知/闹钟式到点提醒：老版本没有（只在回合内软提示），如需是全新功能，单独立项
- 冻结层零改动：core/domain、core/llm、core/data 未动

## 关联

- 上游：V1-013 掌握度读模型（projectDue 本体）、V1-014 学生表达契约（软提示与学生口吻衔接）
- 对照矩阵：`tasks/native-companion-old-main-parity-matrix.md` 第 10 节
