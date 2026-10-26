# 认知与通用语言工具矩阵规范库 (Cognitive Harness Tool Matrix)

> **版本**：v0.1.0 (草案阶段，持续演进与增量合并)  
> **演进方法论**：真实场景自问自答 ➔ 认知算子抽象 ➔ 矩阵张成空间 ➔ 线性代数满秩化简与正交基分解  
> **核心铁律**：严禁把具体学科/业务名词（如椭圆、斜率、语法、锁）硬编码入工具名与契约，必须抽象为通用认知算子。

---

## 一、工具矩阵总览 (Candidate Spanning Space)

当前张成空间的原子认知工具清单（共 6 个初始算子）：

| 序号 | 工具标识 (Tool Identifier) | 中文认知算子名称 | 核心职责 (Core Responsibility) | 状态 |
| :--- | :--- | :--- | :--- | :--- |
| **01** | `audit_system_entities` | 实体动静与自由度审计 | 盘点系统已知量、自由变量与初始拓扑约束，防止无锚推演 | 草稿 (数学场景逼出) |
| **02** | `select_representation_schema` | 表征范式与死角选择 | 评估对象在多种形式化建模下的计算代价与盲区/失效域 | 草稿 (数学场景逼出) |
| **03** | `inspect_blackbox_theorem` | 黑箱模版与定理可靠性检验 | 检验学习者套用的现成公式/设计模式的前提与失真程度 | 草稿 (数学场景逼出) |
| **04** | `trace_dependency_pipeline` | 因果传导与变量依赖追踪 | 建立从输入自变量到目标因变量的单向因果 DAG 链条 | 草稿 (数学场景逼出) |
| **05** | `trace_constraint_source` | 边界与存在性约束溯源器 | 定位诱导终局变量范围的底层物理/代数不可违背条件 | 草稿 (数学场景逼出) |
| **06** | `detect_critical_degeneracy` | 临界退化与系统奇点预警 | 检测系统进入极端/退化/降维边缘时的除零与崩溃风险 | 草稿 (数学场景逼出) |

---

## 二、原子工具契约详情 (Tool Contracts Specification)

### 01. `audit_system_entities`（实体状态与自由度审计）
* **抽象认知背景**：
  在解决任何复杂问题时，第一步永远是盘点盘面上的所有实体，严格区分“确定态（已知/固定）”与“不确定态（自由变量/动态生成）”。
* **输入契约 (Input Schema)**：
  ```json
  {
    "type": "object",
    "properties": {
      "context_description": {
        "type": "string",
        "description": "问题的背景设定与已知初始条件描述"
      },
      "suspected_entities": {
        "type": "array",
        "items": { "type": "string" },
        "description": "当前已观察到的实体或变量候选"
      }
    },
    "required": ["context_description"]
  }
  ```
* **输出契约 (Output Schema)**：
  ```json
  {
    "fixed_entities": [
      { "name": "定点P", "state": "固定于x轴且在椭圆外", "is_invariant": true }
    ],
    "dynamic_entities": [
      { "name": "交点A/B", "degrees_of_freedom": "沿曲线移动的1自由度动点", "source": "割线与几何体相交" }
    ],
    "entity_constraints": [
      "A, B 必须同时满足曲线方程",
      "P, A, B 三点共线"
    ]
  }
  ```

---

### 02. `select_representation_schema`（表征范式与建模形式选择）
* **抽象认知背景**：
  同一现实或抽象对象有多种形式化表达（如直线可用点斜式、截距式、参数式；并发系统可用 Actor 或共享内存）。不同表征范式有各自天然的盲区（如点斜式无法表示斜率不存在），决定后续推演的生死。
* **输入契约 (Input Schema)**：
  ```json
  {
    "type": "object",
    "properties": {
      "target_object_type": {
        "type": "string",
        "description": "需要建模的目标对象（如：过定点的直线、并发状态同步机制）"
      },
      "known_features": {
        "type": "array",
        "items": { "type": "string" },
        "description": "该对象已知的特征约束（如：过定点(x0, y0)、强调读多写少）"
      }
    },
    "required": ["target_object_type"]
  }
  ```
* **输出契约 (Output Schema)**：
  ```json
  {
    "candidate_schemas": [
      {
        "schema_name": "点斜式 (y - y0 = k(x - x0))",
        "computational_cost": "中等代数复杂度",
        "fatal_blind_spot": "无法表达垂直于x轴的直线 (k不存在)",
        "handling_recommendation": "若使用必须强制独立分支讨论垂直情况"
      },
      {
        "schema_name": "横截式 (x = my + x0)",
        "computational_cost": "代数对称性好，对椭圆联立运算友好",
        "fatal_blind_spot": "无法表达与x轴重合的水平线 (m不存在/不可逆)",
        "handling_recommendation": "适合过x轴定点且非水平割线的场景"
      }
    ],
    "optimal_choice": "横截式或显式带完备分支讨论的点斜式"
  }
  ```

---

### 03. `inspect_blackbox_theorem`（黑箱模版与定理可靠性检验）
* **抽象认知背景**：
  学习者经常使用“半生不熟的记忆公式/黑箱模版”试图走捷径。系统必须能检验其调用的现成模版是否在当前语境成立、是否丢掉了关键中间假设。
* **输入契约 (Input Schema)**：
  ```json
  {
    "type": "object",
    "properties": {
      "template_or_theorem_name": {
        "type": "string",
        "description": "学习者主张调用的公式、定理、设计模式或黑箱算法名称"
      },
      "application_context": {
        "type": "string",
        "description": "学习者打算把它用在什么具体的推演步骤上"
      }
    },
    "required": ["template_or_theorem_name"]
  }
  ```
* **输出契约 (Output Schema)**：
  ```json
  {
    "validity": "CONDITIONAL_VALID",
    "strict_prerequisites": [
      "二次曲线方程已化为标准二次多项式",
      "直线与二次曲线相交有且仅有两个互异实根"
    ],
    "high_risk_omissions": [
      "学习者往往只记住两根之和与两根之积的公式，遗漏判别式 Δ > 0 的先决检验",
      "参数退化（二次项系数为0）导致降次为一次方程的情况被忽略"
    ],
    "teaching_countermeasure": "要求学习者展开前置条件证明，而非直接默许公式代入"
  }
  ```

---

### 04. `trace_dependency_pipeline`（因果传导与变量依赖追踪）
* **抽象认知背景**：
  多步复杂推导中，自变量如何一步步传递到最终待求解的因变量？明确数据流向与因果 DAG，可防止推导中途“迷路”或“无头苍蝇式代数乱转”。
* **输入契约 (Input Schema)**：
  ```json
  {
    "type": "object",
    "properties": {
      "source_parameters": {
        "type": "array",
        "items": { "type": "string" },
        "description": "推导的初始驱动变量或自变量（如：直线的斜率 k 或参数 m）"
      },
      "target_variable": {
        "type": "string",
        "description": "最终要求解的目标变量（如：点M的横坐标 x0）"
      },
      "proposed_operations": {
        "type": "array",
        "items": { "type": "string" },
        "description": "学习者规划的中间操作流程"
      }
    },
    "required": ["source_parameters", "target_variable"]
  }
  ```
* **输出契约 (Output Schema)**：
  ```json
  {
    "dependency_dag": [
      { "step": 1, "action": "联立方程与韦达定理", "produces": "x1+x2, y1+y2 (线段AB中点坐标)" },
      { "step": 2, "action": "负倒数求垂直平分线斜率", "produces": "k_perp = -1/k" },
      { "step": 3, "action": "点斜式表达平分线并令 y=0", "produces": "x0 = f(k) 或 x0 = g(m)" }
    ],
    "is_pipeline_closed": true,
    "computational_bottleneck_step": 1
  }
  ```

---

### 05. `trace_constraint_source`（边界与存在性约束溯源器）
* **抽象认知背景**：
  终局变量的“范围、极值、边界”不是凭空出来的，必定源自系统底层某个物理边界、存在性判别或容量上限。该工具用于精准定位约束诱导源。
* **输入契约 (Input Schema)**：
  ```json
  {
    "type": "object",
    "properties": {
      "target_variable": {
        "type": "string",
        "description": "需要求解取值范围的目标量（如：x0）"
      },
      "underlying_system_rules": {
        "type": "array",
        "items": { "type": "string" },
        "description": "当前系统运行中不可突破的物理/逻辑法则"
      }
    },
    "required": ["target_variable"]
  }
  ```
* **输出契约 (Output Schema)**：
  ```json
  {
    "fundamental_constraint_origin": "相交割线的几何存在性（割线必须穿透椭圆生成两个互异交点）",
    "formal_inequality": "一元二次方程根的判别式 Δ(k) > 0 或 Δ(m) > 0",
    "derived_parameter_bounds": "参数 k 的不可达区间或 m 的绝对值下限",
    "boundary_coupling": "通过 x0 = f(k) 将参数约束映射为 x0 的闭/开区间范围"
  }
  ```

---

### 06. `detect_critical_degeneracy`（临界退化与系统奇点预警）
* **抽象认知背景**：
  连续推演一旦遇到离散奇点（分母为零、直线垂直、两点重合、极端退化），常规解法当场崩塌。该工具负责侦测这些破坏性的临界状态。
* **输入契约 (Input Schema)**：
  ```json
  {
    "type": "object",
    "properties": {
      "active_assumptions": {
        "type": "array",
        "items": { "type": "string" },
        "description": "当前推演中潜意识默认的前提假设（如：直线有斜率k）"
      },
      "system_topology": {
        "type": "string",
        "description": "当前的拓扑构型与几何/逻辑关系"
      }
    },
    "required": ["active_assumptions"]
  }
  ```
* **输出契约 (Output Schema)**：
  ```json
  {
    "degeneracy_warnings": [
      {
        "case_name": "垂线奇点 (Vertical Singularity)",
        "trigger_condition": "直线 l 垂直于 x 轴 (斜率不存在)",
        "consequence": "点斜式方程无意义，联立推导完全失效；此时线段AB直接关于x轴对称，中垂线即为x轴本身，交点M处于特殊退化态"
      },
      {
        "case_name": "切线退化 (Tangency Degeneracy)",
        "trigger_condition": "Δ = 0 (两交点A、B重合)",
        "consequence": "不再存在割线段AB，垂直平分线失去几何定义"
      }
    ],
    "verification_action": "必须将垂线奇点作为独立首要 Case 提前单独求解"
  }
  ```

---

## 三、两类实现形态：确定性能力 vs 结构化推理骨架

> 2026-10-27 用户拍板。上层抽象出的所有算子，落地时必然分属这两类之一。
> **这不是可选项，是架构约束**。

### Type 1 · 确定性能力工具 (Deterministic Capability Tool)

- **形态**：真实 function call。代码里真跑算法、真查数据、真写数据库。
- **谁干活**：宿主代码。模型只负责决定「要不要调、传什么参」。
- **返回值性质**：**客观事实**。同一个入参，任何时候返回同一结果。
- **判定标准（三条全中才是 Type 1）**：
  1. 结果不依赖模型的语言理解；
  2. 可以写单元测试断言输出；
  3. 换个模型来调，结果不变。
- **当前清单（预计 6~10 个）**：
  | 工具 | 为什么是确定性 |
  |---|---|
  | `search_course_material` | 向量/关键词检索，命中即事实 |
  | `inspect_common_misconceptions` | 查预置误区库，客观返回 |
  | `locate_missing_warrant` | 词表命中（"显然/同理/易证"）+ 上下文断言缺失检测 |
  | `detect_fluency_illusion` | 统计近 N 轮用户产出 token/句式，纯计数 |
  | `localize_confusion` | 结构化槽位填充，落库可查 |
  | `detect_critical_degeneracy` | 对已解析表达式做符号检查（分母为零、定义域越界） |
  | `record_student_mental_state` | 写记忆，幂等 |
  | `update_knowledge_graph` | 写图谱节点/边，幂等 |
  | `build_argument_graph` | 模型抽节点 + **图算法查悬空**（图算法部分是确定的） |

### Type 2 · 结构化推理骨架 (Structured Reasoning Scaffold)

- **形态**：**提示词注入的强制输出结构**。不是 function call。
- **谁干活**：模型自己判断。但**必须按给定的槽位输出**，不能跳过、不能改结构。
- **约束方式**：要求模型在生成回复**之前**，先填一段结构化自检（如 `<pedagogy_eval>` 块），字段固定。填不出来 = 这一轮不合格。
- **为什么这不违反「禁止拆东墙补西墙」红线**：
  - 贴胶带式 Prompt 是「针对某个具体错误现象追加一句咒语」——现象变了就失效，且不断累积。
  - 结构化骨架是「针对**一整类认知动作**定义输出契约」——它约束的是**推理形式**，不是**具体内容**。
  - 判据：**删掉这条骨架，模型的输出结构会退化；保留它，任何学科场景都受同一套约束。** 满足前者即为骨架，只满足"某句话不说了"的是胶带。
- **当前清单（约 30 个）**：
  `classify_feedback_level`（Hattie 四级）、`decompose_argument`（Toulmin 六要素）、`classify_refutation_response`（Lakatos 四种应对）、`construct_elenchus`（诘问链）、`map_analogy_structure`（Gentner 结构映射）、`probe_usage_not_essence`、`check_speech_act_felicity`、`detect_maxim_violation`、`estimate_cognitive_load`、`decide_scaffold_fade`、`check_step_granularity`、`assess_skill_stage`、`elicit_self_explanation`、`elicit_reflection_on_action`、`detect_banking_mode`、`invoke_heuristic`、`retrospect_check`、`define_verifiable_dod`、`backward_design_audit`、`verify_mastery_gate`、`reconstruct_original_question`、`detect_horizon_mismatch`、`manage_aporia_landing` 等。

### 为什么必须分这两类（踩坑预警）

若把 Type 2 的 35 个硬写成规则算法：
- 每一个都会**比模型自己判断更差**（`classify_feedback_level` 用正则怎么判"这句是任务级还是自我级"？）；
- 会丧失跨学科泛化能力，退化成当初被否决的「固定管线状态机」，只是换了张皮。

---

## 四、矩阵化简与正交化备忘录 (SVD & Orthogonalization Backlog)

*注：本小节在多场景验证后，记录工具之间的投影与合并，保持矩阵满秩。*

* **候选关联记录 1**：`detect_critical_degeneracy`（退化）与 `select_representation_schema`（范式死角）在几何建模上存在重叠投影。  
  *初步观察*：表征选择偏重于**事前防御（选什么工具）**，而临界退化偏重于**事中质检（有没有漏讨论）**。暂保留为两个算子，待更多非数学场景冲击后再评估是否合并。
* **后续场景输入待办**：
  1. 英语阅读/翻译中长难句的“退化与结构断裂”比对。
  2. 操作系统并发/死锁中“资源依赖与奇点”比对。
