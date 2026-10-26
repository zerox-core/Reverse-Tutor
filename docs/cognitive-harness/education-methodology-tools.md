# 教育方法论 ➔ 通用认知工具 抽象清单

> **来源纪律**：每一条都必须是「书里的方法论」→「它治的是哪一类认知失败」→「抽象成的工具契约」。
> 严禁出现具体学科名词（椭圆、从句、死锁），只允许出现认知动作。
> **本文档与 `cognitive-tool-matrix.md` 互补**：那份是从真实对话里逼出来的，这份是从成熟理论里借来的，最后合并化简。

---

## A 组 · 问题求解与推理

### A1. Polya《How to Solve It》(1945)

- **方法论**：解题四阶段（理解问题 → 拟定计划 → 执行 → 回顾）＋ 一张启发法清单：类比、特例化、一般化、倒推、分解重组、引入辅助问题。
- **治的失败**：学习者「卡住」往往不是不会，而是**手边没有可调用的启发动作库**，于是要么放弃，要么开始暴力试错。
- **抽象工具**：
  - `invoke_heuristic`（启发式算子调度）
    - 入参：卡点描述、已尝试过的动作
    - 出参：3 个候选启发动作（特例化/倒推/类比/分解），每个附「为什么它在这个卡点上可能有效」
  - `retrospect_check`（回顾检验）
    - 治的是 Polya 第四阶段被跳过这件事——做完就交卷
    - 入参：已完成的推导过程
    - 出参：能否验算、能否换一条路重推、结论能否推广或收缩到特例
- **为什么重要**：`retrospect_check` 恰好对应「解析几何想拿满分」——最后那 2 分就是回顾阶段验出来的。

### A2. Lakatos《Proofs and Refutations》(1976)

- **方法论**：数学不是「定理 + 证明」，而是**猜想—证明—反例—修补**的对话。反例出现后有几种应对：怪物排除法（改定义把反例赶出去）、例外排除法（承认反例但划为例外）、引理并入法（把反例暴露的隐藏前提吸收进证明，内容增长）。
- **治的失败**：学习者撞到反例时，第一反应是「这题出错了」或「这是特例，不影响」，而不是把反例当成知识增长的入口。
- **抽象工具**：
  - `classify_refutation_response`（反驳应对策略识别）
    - 入参：原有主张、反例、学习者对反例的回应
    - 出参：他采用的是哪种应对（排除 / 划例外 / 修补 / 无视），以及这次应对是**进步的**（内容增加）还是**退化的**（只为保住原主张）
- **为什么重要**：这是把「反驳」分了质量等级。同样是「我觉得这不算反例」，一个是真有道理，一个是在耍赖，这个工具能区分开。

### A3. Toulmin《The Uses of Argument》(1958)

- **方法论**：论证六要素——主张 Claim、依据 Data、保证 Warrant、支援 Backing、限定 Qualifier、反驳 Rebuttal。
- **治的失败**：学习者只给「结论 + 例子」，缺了**保证**（为什么这个依据能推到那个结论）。这就是「跳步」的结构化定义。
- **抽象工具**：
  - `decompose_argument`（论证结构解剖）：输出六要素，并标出缺失项
  - `locate_missing_warrant`（保证缺失定位）：专门抓「显然可得」「同理可证」这类空洞连接

### A4. van Gelder《Argument Mapping》(2001)

- **方法论**：把论证画成节点图，隐藏前提一旦画出来就无处可藏，逻辑结构变得可检验。
- **抽象工具**：`build_argument_graph`（论证图构建）——把一段口头讲解转成「节点 + 支撑箭头」，暴露悬空前提出处。

---

## B 组 · 认知负荷与指导强度

### B5. Sweller 认知负荷理论

- **方法论**：工作记忆容量极小；负荷分三类——内在（材料本身复杂）、外在（呈现方式糟糕）、相关（用于建图式）。**范例效应**：新手看完整worked example 优于自己摸索；**专长逆转效应**：练熟之后，范例反而拖累学习。
- **治的失败**：给新手扔一个空白难题（负荷爆炸），或给老手还一板一眼拆解（侮辱性冗余）。
- **抽象工具**：
  - `estimate_cognitive_load`（认知负荷估算）
    - 入参：学习者近几轮表现、当前材料复杂度
    - 出参：三类负荷的粗估 + 建议（给完整范例 / 给半成品 / 给空白）
  - `decide_scaffold_fade`（脚手架撤除判定）
    - 治的是范例效应与专长逆转效应的**交界点在哪**
    - 出参：该继续保持脚手架、该部分撤除、该完全撤除

### B6. Rosenshine《Principles of Instruction》(2012)

- **方法论**：17 条，核心是：小步子推进、每步大量提问、检查理解、提供范例、引导练习、独立练习、周月回顾。
- **治的失败**：一步跨太大（学生跟丢）、或从不回顾（学完就忘）。
- **抽象工具**：
  - `check_step_granularity`（步长粒度检查）——当前这一步是否超出工作记忆可承载的跳跃幅度
  - `schedule_cumulative_review`（累积回顾调度）——把旧知识点回插进当前对话

---

## C 组 · 记忆与提取

### C7. Bjork《Desirable Difficulties》(1994)

- **方法论**：让学习「当下更难受」但「长期更牢」的操作：提取练习、间隔、交错、生成效应。反之，流畅感往往是错觉。
- **治的失败**：用户觉得「听懂了」= 学会了。这是最大的自我欺骗来源。
- **抽象工具**：
  - `inject_desirable_difficulty`（合意困难注入）
    - 入参：当前讲解流畅度、已停留轮数
    - 出参：建议插入的困难类型（让他复述 / 打乱顺序 / 隔一轮再问）
  - `detect_fluency_illusion`（流畅错觉检测）——用户连续点头但从未产出，警报

### C8. Chi《Self-Explanation Effect》(1994)

- **方法论**：能解释「为什么」的学习者，成绩显著高于只会说「是什么」的学习者。
- **抽象工具**：`elicit_self_explanation`（诱发自解释）——把「下一步做什么」的提问，改写成「这一步为什么必须这样」。

### C9. Roediger & Karpicke 提取练习

- **方法论**：测试本身就是学习，比重复阅读有效得多。
- **抽象工具**：`convert_to_retrieval_prompt`（讲授转提取）——把「我告诉你」转成「你先说，我再补」。

---

## D 组 · 目标与评估

### D10. Wiggins & McTighe《Understanding by Design》(1998)

- **方法论**：逆向设计三阶段——先定**期望结果**，再定**评估证据**，最后才设计学习活动。顺序反过来就会变成「活动导向」的空转。
- **治的失败**：这恰好是「创建链路只填了个标题」的病根——没有先定终局标准。
- **抽象工具**：
  - `backward_design_audit`（逆向设计审计）
    - 入参：当前学习目标、当前设计的活动序列
    - 出参：终局结果是否可被第三方验证、评估证据是否存在、活动是否真的指向结果
  - `define_verifiable_dod`（可验证终局标准定义）——把「学完这章」这种伪目标，逼成「能独立做出 X」

### D11. Bloom 掌握学习 / Two Sigma

- **方法论**：一对一辅导 + 掌握学习，效果约两个标准差。核心是**过关才放行**，而不是按时间推。
- **抽象工具**：`verify_mastery_gate`（掌握度闸门）——判定当前节点是否真的过关，还是被时间推着走。

### D12. Hattie《Visible Learning》(2009)

- **方法论**：反馈分四级——任务级（FT，这道题哪里错）、过程级（FP，你的方法哪里出问题）、自我调节级（FR，你怎么监控自己的思路）、自我级（FS，你真聪明）。**自我级反馈几乎无效甚至有害**。
- **抽象工具**：`classify_feedback_level`（反馈层级分类）
  - 治的失败：AI 学生整天说「老师你讲得真好」，这就是纯 FS 级，零信息量
  - 出参：当前这句反馈属于哪一级，并给出升级到 FP/FR 的改写

---

## E 组 · 发展阶段与教学关系

### E13. Dreyfus 五阶段技能习得模型

- **方法论**：新手（严格按规则）→ 高级新手 → 胜任者 → 精通者 → 专家（直觉判断）。不同阶段需要完全不同的指导方式。
- **抽象工具**：`assess_skill_stage`（学习者阶段判定）——出参：阶段 + 该阶段该给规则还是该给情境。

### E14. Schön《The Reflective Practitioner》(1983)

- **方法论**：行动中反思（reflection-in-action）与行动后反思（reflection-on-action）。专业能力来自对「当时为什么那样做」的回溯。
- **抽象工具**：`elicit_reflection_on_action`（行动反思诱发）——在用户做对之后，问「你刚才是怎么想到这一步的」，而不是只说「对了」。

### E15. Freire《被压迫者教育学》(1970)

- **方法论**：储蓄式教育（banking model，老师存、学生取）vs 提问式教育（problem-posing，师生共同探究）。
- **治的失败**：AI 一旦变成「标准答案播报机」，整套反转教学就死了。
- **抽象工具**：`detect_banking_mode`（灌输模式检测）——检测当前对话是否退化为单向传输，并给出打断点。

### E16. Gentner《Structure Mapping Theory》(1983)

- **方法论**：类比不是表面相似，而是**关系结构**的映射。好的类比映射的是因果/关系骨架，坏的类比只映射了表面特征。
- **抽象工具**：`map_analogy_structure`（类比结构映射）
  - 入参：源域、目标域
  - 出参：哪些关系被正确映射、哪些只停留在表面相似、哪些关系在目标域里**没有对应物**（类比失效点）

---

## 汇总：16 本书 ➔ 22 个候选工具

| 来源 | 工具 | 治的失败类 |
|---|---|---|
| Polya | `invoke_heuristic` | 卡住时无动作可调 |
| Polya | `retrospect_check` | 做完不回顾 |
| Lakatos | `classify_refutation_response` | 遇到反例耍赖 |
| Toulmin | `decompose_argument` | 只给结论不给论证 |
| Toulmin | `locate_missing_warrant` | 跳步 |
| van Gelder | `build_argument_graph` | 隐藏前提不可见 |
| Sweller | `estimate_cognitive_load` | 负荷爆炸/冗余 |
| Sweller | `decide_scaffold_fade` | 脚手架该撤不撤 |
| Rosenshine | `check_step_granularity` | 步子太大 |
| Rosenshine | `schedule_cumulative_review` | 学完就忘 |
| Bjork | `inject_desirable_difficulty` | 学得太舒服 |
| Bjork | `detect_fluency_illusion` | 流畅感错觉 |
| Chi | `elicit_self_explanation` | 只说是什么 |
| Roediger | `convert_to_retrieval_prompt` | 只输入不提取 |
| UbD | `backward_design_audit` | 活动导向空转 |
| UbD | `define_verifiable_dod` | 目标不可验证 |
| Bloom | `verify_mastery_gate` | 时间推着走 |
| Hattie | `classify_feedback_level` | 空泛表扬 |
| Dreyfus | `assess_skill_stage` | 阶段错配 |
| Schön | `elicit_reflection_on_action` | 做对但不知道为啥对 |
| Freire | `detect_banking_mode` | 退化为灌输 |
| Gentner | `map_analogy_structure` | 表面类比 |
