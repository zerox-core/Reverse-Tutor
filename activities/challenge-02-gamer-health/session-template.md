# 会话模板 challenge-gamer-health-7d-v1（内容定义）

> 该模板 id 挂在挑战活动实例的 `session_template_id` 上。
> 本文件定义模板承载的内容；模板在会话创建链路的接线（prefill 注入）属于 I3 的工作。

## 模板携带的内容

1. **知识包引用**：`activities/challenge-02-gamer-health/`（KP 树 = README 知识地图 S1-S3，误解条目 = misconceptions.yaml，引导 = onboarding-d0.md，评测细则 = rubric.yaml，资料库 = sources/）。
2. **7 天编排**：days/day-01..07.md；服务端种入 `activity_tasks`，端上按天取用；D0 引导不占天数。
3. **学生人设**：student-card.yaml 的 session_defaults（learnerRole / learnerProfile / dialogueStrategy / openingMessage）；人物配方取值待人物融合机制对齐。
4. **每日会话配置（I3 注入点）**：
   - goal = 当日 stage_goal + 活动总目标
   - plan = 当日 task_markdown
   - openingMessage = 每天开场三句话：今天教哪块（阶段位置）/ 教成什么样算完（stage_goal）/ 7 天进度
   - sourceSelections = 知识包引用（沿用 canonicalActivitySource(activity.id)）
5. **评测注入点（D7 及学生主动要计划时）**：
   - 触发：学生发出「给我定个计划」类请求（D7 为剧本内触发，其余天为学生人设自然发起）
   - 流程：收集老师计划 → LLM 按 rubric.yaml 结构化评分（json mode，复用 chat_json 重试链）→ 总分 + 五维度得分/落档/点评进会话 → 允许修订复评 1 次
   - 记录：评分结果与计划文本进 mastery 账本，作为 D7 验收的 delayed 证据之一

## 完成判据（草案）

每阶段至少 1 条 transfer 或 correction 的 passed 证据；D7 完成一次计划核对评测闭环（含 rubric 打分记录）；纯 explanation 不算完成。判据接源码 mastery 闸门，不在模板层另造。

## 边界

- 无打卡、无催收：本挑战按拍板走碎片化自由节奏，模板不含任何打卡/断签逻辑。
- 学生不主动产出训练/饮食计划（凯圣王内容只到概念层）；计划永远由老师产出、rubric 评分。
- 模板不写死活动日期、排行榜等运营信息（运营层只存在活动实例上）；机制层（表达契约/纠错阶梯/掌握度闸门）一律以源码为准。
