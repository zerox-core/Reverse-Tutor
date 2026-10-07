# NEWMP-V1-009 · S3 V1 完成度对齐映射（engine.py ↔ mobile-native）

> 阶段：S3（V1 完成门）｜日期：2026-09-09（周三）｜分支：newmp
> 方法：逐段读 `engine.py`（53.5KB）turn 主流程，对照 mobile-native 11 模块源码检索取证。
> 结论先行：**V1 核心「单会话反转教学回合环」已对齐；掌握度/复习/摘要/锚点/检索注入等持久化与增强件尚未落地**，且这些缺口大多落在主线宪章 V2+ 阶段，应由 S4–S7 一并覆盖。

## 一、对齐结果总表

| # | engine.py 能力 | mobile-native 对应 | 状态 |
|---|---|---|---|
| 1 | Session 创建 + 模式（study/goal） | core:data/session + `SessionModeWire`（study/goal/companion） | ✅ 对齐 |
| 2 | Turn 评估+动作决策（结构化 evaluation/action） | core:domain `SessionTurnPolicy`+`SessionTurnContracts`，core:llm `StructuredTurnOutcome` | ✅ 对齐（V1-006 单会话闭环） |
| 3 | 教学动作选择（probe/challenge/clue/scaffold/examiner_verify/recap…） | `TeachingActionSelector` + `ActionTypeWire`（词表完整） | ✅ 对齐 |
| 4 | 策略设置（probing_intensity / correction_timing / correction_persistence） | `SessionStrategySettings` | ✅ 对齐 |
| 5 | 错误日志（misconception / error_pattern 记录） | core:data/memory `ErrorLogInput` + `MemoryRepository.logError` | ✅ 对齐 |
| 6 | 掌握度 upsert + 证据类型（explanation/retrieval/transfer/delayed_retrieval/correction） | 词表已迁移（`MasteryEvidenceContract`/`MasteryEvidenceTypeWire`）但 **无落库**：core:data 无 mastery 表 / upsert | ❌ 缺口 |
| 7 | 到期复习（due_reviews + mark_review_pending + 复习频率） | 无对应（无 dueReview） | ❌ 缺口 |
| 8 | 历史压缩摘要（maybe_summarize，防漂移+控 token） | core:data 无 summarize 持久化；`ConversationContextAssembler` 是否做裁剪待核 | ❌ 缺口 |
| 9 | 锚点（anchors：记录需求/目标，供系统提示引用） | 无 Anchor 持久化 | ❌ 缺口 |
| 10 | KG 抽取 + kg_context + clue 检索注入 + 引用纪律（fake_citation/no_citation） | core:data/graph + `SourceGroundedCheckPolicy` 部分存在；引用纪律需核 | ⚠️ 部分 |
| 11 | Web 检索导入（websearch → add_document → chunk → 注入） | 无 web_search | ❌ 缺口 |
| 12 | 开场轮（run_opening_turn） | NewSession「开场消息」字段（确定性模板，创建即落首条 Assistant 消息） | ⚠️ 形态不同（模板 vs LLM 生成） |
| 13 | 运行时记忆提示（runtime_memory_hint） | `ConversationContextAssembler`（聚合记忆/错误/图谱缺口/复习点/来源证据） | ⚠️ 形态不同（聚合式，已精读确认） |

## 二、判定依据（可追溯）

- `engine.py` `run_turn`（L1230 起）：maybe_summarize → list_anchors/masteries/due_reviews/error_logs → build_runtime_memory_hint + kg_context → build_system_prompt → clue 检索注入 → llm.chat_json → _normalize_turn_payload → 落库 user/assistant → upsert_mastery / upsert_error_log / resolve_error_pattern → add_anchor → kg_gate 抽取 → due_reviews 跳过挂起 → commit。
- mobile-native：`core:domain` 含 `SessionTurnPolicy/SessionTurnContracts/TeachingActionSelector/ConversationContextAssembler/ConversationRunCoordinator/GuidedLearning*/LearningScopeGuard/CompanionMemory*/WindowTopology*/Initiative*` 等 29 个文件；`core:data` 含 session/message/memory/learning/heartbeat/window/graph/sources/search/background 等 23 个仓库目录。
- 对 `core:data/src/main` 检索 `mastery|Mastery|review_due|dueReview|SummaryMessage|summarize`：**0 命中** → 掌握度/复习/摘要持久化确不存在。
- `SessionTurnContracts` 已完整迁移 engine.py 词表：模式、entry 状态、用户情绪、学生角色（含 clue_student/scaffold_student/examiner）、掌握度证据类型、动作类型、纠错时机/持久度——证明决策环语义已对齐，缺的是持久化层。

## 三、缺口归属与后续安排

- 缺口 6/7（掌握度+复习）：属宪章 V2「记忆拓扑」/ V5「学习事实」范畴，归入 **S4–S7 核查清单**，不阻塞 V1 闭环判定。
- 缺口 8（摘要压缩）：长会话防漂移能力，建议作为 V1 收尾增强项单独立一小任务。
- 缺口 9（锚点）：属系统提示上下文管理，随 V2 记忆拓扑一并处理。
- 缺口 10/11（检索注入/引用纪律、Web 导入）：属增强件，优先级低于核心闭环；引用纪律需单独取证。
- 定性 12/13（2026-09-09 定点核对，见 NEWMP-V1-010 第 3 节）：开场轮 = NewSession「开场消息」确定性模板（默认「准备好后，请开始讲给我听吧。」、预设为角色自述+剧情，UI 可编辑），创建会话即落首条 Assistant 消息——与 engine.py LLM 生成开场为形态差异而非缺失；运行时记忆提示 = `ConversationContextAssembler` 聚合近况消息/记忆引用/历史错误/图谱缺口/待复习点/来源证据六类进回合上下文，较 engine.py 分散注入组织更聚合。

## 四、本轮结论

V1 完成门的核心判据「反转教学单会话回合环（评估→决策→行动→错误日志落库）」在 mobile-native **已达成**；engine.py 中属于记忆增强与复习调度的持久化件**尚未迁移**，已逐条登记并归属后续阶段。S3 至此定性完成，等待 S4–S7 复核后出全量报告。
