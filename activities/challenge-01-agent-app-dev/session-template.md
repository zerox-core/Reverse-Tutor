# 会话模板 challenge-agent-app-dev-17d-v1（内容定义）

> 该模板 id 挂在首期活动 `challenge-agent-app-dev-17d` 的 `session_template_id` 上。
> 本文件定义模板承载的内容；模板在会话创建链路的接线（prefill 注入）属于 I3 的工作。

## 模板携带的内容

1. **知识包引用**：`activities/challenge-01-agent-app-dev/`（KP 树 = README 知识地图 M0-M7，误解条目 = misconceptions.yaml，引导 = onboarding-d0.md）。
2. **17 天编排**：days/day-01..17.md；服务端已种入 `activity_tasks`，端上按天取用。
3. **学生人设**：student-card.yaml 的 session_defaults（learnerRole / learnerProfile / dialogueStrategy / openingMessage）；人物配方取值待 §10-4 拍板。
4. **每日会话配置（I3 注入点）**：
   - goal = 当日 stage_goal + 活动总目标
   - plan = 当日 task_markdown
   - openingMessage = 每天开场三句话：今天教第几章（模块位置）/ 教成什么样算完（stage_goal）/ 全书什么位置（17 天进度）
   - sourceSelections = 知识包引用（沿用 canonicalActivitySource(activity.id)）

## 完成判据（草案，待 §10-1 拍板）

每模块至少 1 条 transfer 或 correction 的 passed 证据；D17 产出 delayed_retrieval 证据；纯 explanation 不算完成。判据接源码 mastery 闸门，不在模板层另造。

## 边界

模板不写死活动日期、排行榜等运营信息（运营层只存在活动实例上）；机制层（表达契约/纠错阶梯/掌握度闸门）一律以源码为准。
