# 反转家教算法内核移植映射表（spike → mobile-native）

> 规格源：`/mnt/f/xw/reverse-tutor-web-algo-spike`（31 工具、LLM 自主决策循环、160 测试基线）
> 目标：`mobile-native/feature/reversetutor`（新模块，纯算法内核，零冻结区侵入）
> 移植原则：**只 import 冻结区，不修改**（AGENTS.md §0.1）；工具=事实层、LLM=决策层

## 1. 架构对位

| spike（Python Web） | mobile-native（Kotlin） |
|---|---|
| `app/engine/orchestrator.py` 工具循环 | `feature/reversetutor/tools/ToolLoop.kt`（自建，走 core:llm 的 `ProviderHttpTransport` 只读复用） |
| `app/llm/provider.py` OpenAI function calling | 自建 payload（tools/tool_choice/messages 多轮）——现有 runtime 单轮无 tools，不改冻结代码 |
| `app/tools/registry.py` REGISTRY | `tools/ToolRegistry.kt`（首批核心 15 工具） |
| `app/engine/session_service.py` 状态 | `session/ReverseTutorSession.kt`（data class + 内存仓储，阶段 3 接 Room 独立表） |
| `app/engine/student_card.py` 角色卡 | `card/StudentCards.kt` + `assets/student_cards.json`（169 条台词，内容与代码分离） |
| `app/tools/curriculum.py` 课标 | `curriculum/CurriculumCatalog.kt` + `assets/curriculum.json`（30 概念） |
| `app/tools/mathcheck.py` 数学守卫 | `guard/MathGuard.kt`（纯正则，无 sympy） |
| `tests/` 160 用例 | `src/test/` 对拍测试（同输入断言同输出） |

## 2. 31 工具 → Kotlin 映射与分期

**阶段 1 纯计算（首批核心，零依赖直译）**

| # | 工具 | spike 源 | Kotlin 落点 |
|---|---|---|---|
| 1 | lookup_curriculum | curriculum.py | CurriculumCatalog.findInCurriculum + conceptStatus（5 级优先级） |
| 2 | update_knowledge_graph（计算部分） | memory.py | KnowledgeGraphBuilder（28 课标先修边 + 节点 enrich） |
| 3 | express_partial_understanding | cognitive.py | ScoreEngine |
| 4 | make_deliberate_mistake | persona.py | MistakeTools（含 MathGuard 豁免标记） |
| 5 | design_counterexample | cognitive.py | CounterexampleTools |
| 6 | scaffold_problem | strategy.py | ScaffoldTools |
| 7 | challenge_teacher_explanation | persona.py | 边界检查 + 事件 |
| 8 | log_breakthrough | memory.py | EventLog |
| 9 | detect_teaching_failure | evaluation.py | ScoreEngine |
| 10 | evaluate_session_quality | evaluation.py | ScoreEngine |
| 11 | simulate_breakthrough | cognitive.py | ScoreEngine |
| 12 | plan_session_closure | routing.py | RoutingTools |
| 13 | decide_next_action | strategy.py | RoutingTools |
| 14 | search_materials（内存版） | retrieval.py | MaterialIndex |
| 15 | speak_as_confused_student（边界检查） | persona.py | PersonaBoundary |
| — | check_math（基础设施） | mathcheck.py | MathGuard.scanMath |
| — | build_system_prompt | prompts.py | PromptAssembler |

**阶段 2 决策循环**：ToolRegistry + ToolLoop（function calling 协议 + JSON 修复兜底）+ 失败降级
**阶段 3 存储**：Room 独立表（不改现有 ReverseTutorDatabase migration 链）
**阶段 4 提示词**：PromptAssembler 完整版（身份铁律+人设+卡+状态+工具说明）
**阶段 5 验证**：误导/跳步/下套三场景剧本复跑
**二批**：web_search、retrieve_historical_context、generate_progress_report、save_teaching_pattern、measure_learning_gain 等

## 3. 硬规则遵守清单

- 冻结区（core/model、core/protocol、core/llm、core/data/*、SecretStore、Room schema）：**只读**
- 新代码全部在 `feature/reversetutor`（AGENTS.md 允许新增区域）
- 测试不打真实网络（Mock LLM）；Python 主仓库 320 基线不受影响
- 不 git push，留工作树待审核

## 4. 对拍验收标准

- CurriculumCatalog：概念数 30、别名 8、find/concept_status 用例与 Python 输出一致
- MathGuard：4 条错型正则命中/不命中、verify_completing_square(6) 与 Python 一致
- StudentCards：三卡 8 桶 169 条、select_buckets 全信号路径（顿悟窗口/拆穿/盲区/低水平/权威压力/默认/收尾占位）
