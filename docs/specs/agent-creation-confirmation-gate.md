# 创建链「确认闸门」改造设计（R102 · 路线 A）

- **状态**：待用户评审——评审通过前不动任何代码
- **日期**：2026-10-06
- **依据**：R101 创建链审计结论（三个根因）+ 用户 2026-10-06 拍板：路线 A（确认闸门优先，现有状态机不动结构）+ 先出本文档
- **前置阅读**：R101 审计（创建链调用链与根因）、R100 状态机（AgentCreationGraph 七态）

---

## 1. 背景：要根治的三个问题

R101 审计（代码实证）定位了创建链的三个根因，本设计全部覆盖：

1. **伪选择**：追问文案（followUpQuestion）100% 由模型自由生成，CONTRACT 提示词里没有任何「选项质量」规则——用户说「我啥都不会」，模型却给「基础还原 vs 速拧/盲拧」这种按难度分层的假选项。
2. **替用户做选择**：契约允许模型每轮往 draft 任意写字段，客户端没有「关键字段必须用户亲口确认」的闸门；goal 一旦被模型写入，title 客户端兜底（goal 首句 14 字）立刻凑齐两个必填项 → 60 分封顶解除 → 融合分单调不减 → ≥85 自动收敛。用户一句反问「我该学什么呀？」被当成了信息足够。
3. **分数驱动流程**：状态机转移的实际决定者是模型自评分（fused = 0.5×模型自评 + 0.5×确定性分），代码层只是被动执行。

## 2. 目标与非目标

**目标**

- 流程走向的决定权从模型手里收回代码层：转移条件从「分数驱动」改为「确认驱动」。
- 模型只能「提案」，永远不能自己把字段写死。
- CONTRACT 补选项质量规则，止住伪选择。

**非目标（明确不做）**

- 不动 AgentCreationGraph 七态结构（CollectGoal / Interview / DraftProposal / ConfirmDraft / GenerateProfile / GenerateLearningFlow / Done）。
- 不动网关、模型调度、流式链路。
- 不上云端 LangGraph（用户已拍板否决云端化）。
- 不改创建屏 UI 主结构。

## 3. 核心设计：槽位（Slot）与确认协议

### 3.1 槽位模型

新增 `AgentCreationSlots.kt`，把创建要收集的信息建模为带状态的槽位：

| 槽位 | 必填性 | 说明 |
|---|---|---|
| Goal | 必填 | 学习目标 |
| LearnerRole | 必填 | 学员角色/基础 |
| Persona | 可选 | 家教人设 |
| TeachingStyle | 可选 | 教学方式 |
| Constraints | 可选 | 约束条件 |
| Title | 派生 | 不允许模型直接写，只能由 Confirmed 的 Goal 派生 |

每个槽位三态：

- **Empty**：无内容。
- **Proposed(value, round)**：模型提出了值，但用户尚未确认。模型每轮回包里的 draft 字段**只能落到 Proposed**。
- **Confirmed(value, round)**：用户亲口确认（或直接给出）的值。**只有 Confirmed 的值参与后续流程与 UI 草案展示。**

### 3.2 确认信号（代码判定，模型无权判定）

每轮收到用户文本后，代码层按以下优先级判定各 Proposed 槽位是否转为 Confirmed：

1. **用户直接给值**：用户文本本身就是该槽位的新值或修正值（如「我是零基础大学生」→ LearnerRole 直接 Confirmed）。用户亲口说的，无需再确认。
2. **显式确认词**：上一轮的 followUp 正悬着某槽位提案，且本轮文本命中确认词表（「对 / 是的 / 就这个 / 可以 / 好 / 没错」等，复用并扩展现有 INCUBATION_CONFIRM 正则的做法）。
3. **选项采纳**：本轮文本与提案值高度重合（包含关系或归一化编辑距离 ≤ 阈值）——用户复述了选项。
4. **非正面回答（重点防事故）**：本轮文本是疑问/反问/跑题（句尾「？」「吗/呢/呀」+ 疑问词，或不含任何提案相关内容）→ **提案一律保持 Proposed**，该槽位下一轮必须重新征求，禁止任何形式的推进。

确认信号判定**全部在代码层完成**，模型只负责生成提案与追问文案，对状态转移没有任何投票权。

### 3.3 反例（必须被拦截的旧行为）

- 用户：「我该学什么呀？」→ 旧逻辑：模型把 goal 写成「三阶魔方基础还原」并收敛。**新逻辑：非正面回答，Goal 保持 Empty/Proposed，下一轮继续问。**
- 模型在 followUp 里说「那我们就锁定 X 吧」→ CONTRACT 明确禁止（见 §5），且即使模型写了 draft.goal，没有确认信号也只停在 Proposed，不会触发收敛。

## 4. 状态机转移条件改造（七态结构不变）

| 转移 | 旧条件（分数驱动） | 新条件（确认驱动） |
|---|---|---|
| CollectGoal → Interview | goal 非空 | **Goal = Confirmed** |
| Interview → DraftProposal | fused ≥ 85，或 shouldConverge（轮次/正则） | **必填槽（Goal、LearnerRole）全部 Confirmed**，且满足以下之一：可选槽全部 Confirmed / 用户说「别问了」（STOP_ASKING 正则保留）/ 达到软轮次上限 SOFT_ROUND_CAP=12 |
| DraftProposal → ConfirmDraft | 草案卡已展示 | 不变 |
| ConfirmDraft → GenerateProfile | INCUBATION_CONFIRM 正则 | 不变 |
| GenerateProfile → GenerateLearningFlow → Done | 节点成功 | 不变 |
| Done → Interview（reopenForRevision） | 用户要求修改 | 不变；修改的槽位降回 Proposed，重新走确认 |

配套改动：

- **understanding 分降级为纯 UI 展示**（进度条照常显示），不再作为任何转移判定的输入；`CAP_REQUIRED_MISSING=60` 封顶逻辑删除（必填未确认根本不会前进，封顶已无意义）。
- **title 兜底改造**：`proposeTitle` 只在 Goal = Confirmed 后才允许执行；Goal 未确认时 title 恒为空，杜绝「模型写 goal + 客户端补 title = 瞬间凑齐必填」的旧链路。
- `fuse()`（0.5/0.5 融合）保留用于展示分，但收敛判定不再读它。

## 5. CONTRACT 提示词补充（选项质量规则）

在 `AgentCreationPrompts.kt` 的 CONTRACT 中新增以下规则（中文原文草案）：

1. 「给出的选项必须互斥且真实可选；当用户表示零基础 / 完全不会时，第一个推荐项必须是门槛最低的选项，并用一句话说明推荐理由。」
2. 「选项按种类 / 方向分层，禁止只按难度分层（例如不要在用户零基础时给『基础还原 vs 速拧盲拧』这种对比）。」
3. 「在用户明确确认之前，禁止使用『那我们就锁定 / 就决定 / 就选 X 吧』这类替用户下结论的表述；你只能提议，然后等用户回答。」
4. 「draft 字段只能填写用户在本轮或历史轮次中明确表达或确认过的信息；用户没有提及的字段保持空白。」

## 6. 兼容与迁移

- **进行中的旧创建会话**：现有 draft 里已有值的字段，启动迁移为 Proposed（含 goal——即使是旧数据也要求用户确认一次，由 AI 在下一轮复述提案：「之前我们聊到你想学 X，对吗？」）。
- **持久化**：草稿 JSON 增加 `slotStates` 字段（槽位 → 状态/值/轮次）；缺省时按上一条规则从旧 draft 推导，旧数据不丢。
- **回滚**：改动集中在 §7 列出的文件，全部 `.bak_r102` 备份；出问题整批还原。

## 7. 改动文件清单（预估）

| 文件 | 改动 |
|---|---|
| `AgentCreationSlots.kt`（新增） | 槽位模型、三态、确认信号解析 |
| `AgentCreationGraph.kt` | 转移条件按 §4 表改造 |
| `AgentCreationCoordinator.kt` | draft 合并逻辑改槽位写入；确认信号接线；收敛判定改确认驱动；title 兜底加 Goal=Confirmed 前置 |
| `AgentCreationPrompts.kt` | CONTRACT 新增 §5 四条规则 |
| `AgentCreationUnderstanding.kt` | 分数保留 API、降级为展示用途；删 60 封顶 |
| `AgentCreationFollowUpPlanner.kt` | 追问优先级读槽位状态（Empty/Proposed 优先）替代原字段空判 |
| 测试文件 | 新增槽位/确认信号/转移条件单测；更新受影响的现有测试 |

UI 层预期零改动（草案卡照常展示，但展示的是 Confirmed 值）。

## 8. 测试计划

**新增单测**（全部假模型，不烧额度）：

1. 确认信号四类判定（直接给值 / 显式确认词 / 选项采纳 / 非正面回答拦截）。
2. 槽位状态机：Empty→Proposed→Confirmed；Confirmed 不可被模型回包覆盖。
3. 转移条件：必填未全 Confirmed 时 fused 再高也不进 DraftProposal；STOP_ASKING 与轮次上限兜底仍生效。
4. title 兜底：Goal 未 Confirmed 时 title 恒空。
5. 旧数据迁移：旧 draft.goal → Proposed。

**场景复现测试**（对照用户 2026-10-05 事故）：

- 场景一：用户「我啥都不会」→ 追问选项必须含最低门槛推荐项，且不得出现按难度分层的伪选择。
- 场景二：用户反问「我该学什么呀？」→ Goal 不得落 Confirmed、不收敛、下一轮继续征求。
- 场景三：用户「对，就学三阶」→ Goal Confirmed，流程正常推进到草案卡。

**回归**：feature/chat + app + core/data 全量单测（现 1099 个）+ assembleDebug 全绿后装机真机实测。

## 9. 验收标准

1. §8 三个事故复现场景行为全部符合预期。
2. 全量单测 + 构建全绿。
3. 真机完整走一遍创建流：零基础提问 → 选项合理 → 反问不被替答 → 确认后正常生成草案卡。
4. 与 R99 / R100 / R101 改动一起，由用户拍板后统一提交 git。
