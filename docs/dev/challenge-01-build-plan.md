# 挑战窗口最终版建设计划（challenge-01：教 AI 学会落地 agent 应用开发）

> 2026-09-25 定稿。分支 feat/challenge-activity，用户已全授权按本计划推进，合并冲突由用户人工判断。
> 铁律：机制层一律以源码为唯一事实源（见 challenge-01-agent-app-dev-package.md 文首清单），本计划只建设内容层与挑战窗口链路，不发明机制。

## 1. 现状盘点（代码实证，2026-09-25）

已实现（骨架，空置待填）：
- 服务端：`online_db/models.py` 四表（activities / activity_tasks / activity_participations / activity_progress_events）；`activity_store.py` 完整支持 tasks 单事务写入、乐观锁、幂等事件、状态机；`/api/v1` 列表/详情/加入/进度/退出/排行榜；管理 API（4edef3d）。
- 种子：`catalog_seed.py` 只种了英文示例「21-day Python challenge」，**tasks 为空、无中文活动**。
- 端上：`ChallengeRoute.kt` 活动页 + 详情 BottomSheet（规则行渲染，**无每日任务列表**）；`ChallengeRuntimeCoordinator.kt` 只有 Load/Join 两个操作，**无进度上报**；`ChallengeSessionLaunch.kt` 已实现会话 Create/Reuse 决策与 prefill（goal 取 activity.description，未注入当日任务）；`HttpOnlineApi.kt` 已有 progress 端点（未被调用）。
- 硬编码残留：ChallengeRoute 的 `progress: Int = 12`、FeedbackPill「已回传 3 条 Memory · 2 个薄弱节点」为占位。

关键缺口（本计划要补的）：
1. 首期中文活动内容（知识包，M2 里程碑核心）。
2. F2 每日任务列表 UI（tasks 已在模型与 API 里，端上未渲染）。
3. F4 完成一次会话 → 进度 +1 的端上触发点（API 已有，无人调）。
4. 挑战会话 prefill 注入当日任务与阶段目标（内容链路核心）。
5. 大纲外显「书」形态与掌握度反馈（替换硬编码占位）。
6. 学生卡归并流程（活动结束 → PresetCard 注册，依赖 §10-4 拍板）。

## 2. 建设顺序（5 个增量，逐个提交推送）

### I1 知识包内容层（本轮）
产出 `activities/challenge-01-agent-app-dev/`：
- `README.md` 知识包索引（主题 / 画像 / M0-M7 知识地图 / 17 天编排 / 完成判据草案 / 实测核实点清单）
- `days/day-01.md … day-17.md` 逐日任务（front-matter: day_number/title/stage_goal，正文=task_markdown，直接可种入 activity_tasks）
- `labs.md` M0 四个对照实验指南（含观察记录表、降级方案、实测核实点）
- `onboarding-d0.md` D0 起点测评与环境向导（意图层定义、分支、降级链 L1-L3、六大劝退点预案）
- `misconceptions.yaml` 10 条误解内容条目（喂源码纠错机制）
- `student-card.yaml` 学生卡 PresetCard 草案（人物配方取值待 §10-4 拍板）

风格红线：所有话术遵守表达契约（称呼克制、不逐条罗列、一轮最多一个问题）；示例话术只保留请教角度。未实测的平台细节一律标【实测核实点】，不写死。

### I2 服务端内容接入
- 新增首期中文活动 seed：slug `challenge-agent-app-dev-17d`，state 按运营节奏定，17 条 tasks 从 `days/` 生成（写生成脚本，内容与库单一来源）。
- session_template 内容定义（挂 `session_template_id`，模板内含 KP 树与资料库引用）。
- 校验：`py -m pytest -q --ignore=tests/test_project_homepage.py` 全绿。

### I3 端上内容链路
- F2：详情 BottomSheet 增加每日任务列表（渲染 ActivityRecord.tasks，当天高亮，完成打勾）。
- 挑战会话 prefill 升级：`ChallengeSessionLaunch` 的 configuration 注入当日任务 markdown、stage_goal、「第几天/全书什么位置」三句话开场。
- F4：会话结束（满足完成判据草案）→ 调 progress 端点 +1（幂等键含日期防重）。
- 端上单测同步补。

### I4 大纲外显与掌握度反馈
- 活动页「书」形态：模块目录、每天开场三句话、进度视图（复用学习提示与 TurnNote 信号，不另造机制）。
- 替换硬编码：progress 占位、FeedbackPill 占位改接真实信号。
- 荣誉呈现（徽章/称号文案，视觉最后）。

### I5 学生卡归并 + 收尾
- 活动结束归并流程：PresetCard 注册进全局创建流程（依赖 §10-4）。
- 交接报告（handoff-log 汇总 + 演练结论）。

## 3. 待用户拍板（不阻塞 I1-I3，算法层就绪后逐项确认）
即 package §10 六项：完成判据接 mastery 闸门 / 误解暴露时机接法 / D0 状态映射 / PresetCard 注册与配方取值 / 17 天遗忘调度 / 递话机制接法。

## 4. 每增量验收门禁
- 服务端/端上代码改动：`py -m pytest -q --ignore=tests/test_project_homepage.py` 全绿 + 对应端上单测绿。
- 内容改动：front-matter 字段齐全（day_number/title/stage_goal）、假规则 ≤20 字、无「老师」句句开头的话术。
- 每增量 commit + push origin feat/challenge-activity；只提交本任务线文件。
