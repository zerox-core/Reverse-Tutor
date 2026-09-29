# 遗忘曲线函数设计（D7 未决项落地稿）

日期：2026-09-29 · 状态：待用户拍板数值 · 关联：docs/specs/graph-memory-projection-decisions.md（D2 / D7 / D8 / D9）

## 1. 设计输入（已拍板约束）

- 遗忘作用于**事件层**；节点记忆衰退是事件集体遗忘的涌现（D7）。
- 每条事件带 `effectiveness`（证据闸门）：explanation=0.35 / retrieval=0.55 / transfer=0.72 / delayed_retrieval=0.82 / correction=0.90；partial×0.75；failed 回退 0.08（D2）。
- 事件台账 **append-only**，任何「复习重置」不得以修改旧事件实现（LearningLedgerRepository 契约）。
- 骨架节点保护期 ∞（D8），本函数只管辖枝叶节点与卫星。
- 图谱现有 Dying 动画参数（decayOrbitBoost / decayRadiusPow）是**纯视觉参数**，不在本设计范围。

## 2. 函数形式：事件保持率（指数衰减）

每条事件 i 有一条保持率曲线：

```
R_i(t) = exp( -(t - t_i) / S_i )
```

- `t_i` 事件时间戳；`S_i` 该事件的**记忆稳定度**（单位：天），即保持率衰减到 e⁻¹≈37% 所需时间。
- 选指数衰减的理由：与间隔重复文献（Ebbinghaus / SM-2 / FSRS）的保持率模型一致；本地 O(n) 求值便宜；参数少（每事件只有一个 S），可解释、可调。
- 不用幂律（t^-a）：长尾巴导致「永远忘不干净」，与 D7「坠入核心」的终态语义冲突。

## 3. 稳定度 S 模型（无状态纯函数）

### 3.1 初始稳定度（由证据分决定）

```
S_0 = 24 × eff²   （天）
```

| 证据类型 | eff | S_0 |
|---|---|---|
| explanation | 0.35 | ≈ 2.9 天 |
| retrieval | 0.55 | ≈ 7.3 天 |
| transfer | 0.72 | ≈ 12.4 天 |
| delayed_retrieval | 0.82 | ≈ 16.1 天 |
| correction | 0.90 | ≈ 19.4 天 |

口径：被动听一遍（explanation）约 3 天开始松动；自己纠正过错误（correction）的记忆约 3 周才松动。partial 事件先按 ×0.75 折算 eff 再代入。

### 3.2 复习链增益（间隔重复核心）

同一节点下的**成功复习事件**（retrieval / delayed_retrieval / correction 且非 failed）使该主题当前稳定度倍增：

```
S = S_0 × G^k ，G = 2.0，k = 成功复习次数，S ≤ S_max = 180 天
```

效果序列（以 retrieval 首次 S_0≈7 天为例）：7 → 14 → 28 → 56 → 112 → 180（封顶）。
3 次成功复习后保护期约 1 个月——符合「分布式重复换来长期记忆」的教学直觉。

### 3.3 失败回退（lapse）

failed 事件（回退 0.08）不打断台账，但在稳定度上回退复习链：

```
k_eff = max(0, k_success − 2 × k_lapse)
S = S_0 × G^(k_eff)
```

一次失败抵消两次成功——「会了又错」的记忆回到近新手期，会较快重新进入断链流程，给用户真实的「这章得重学」信号。

### 3.4 为什么不需要新表

S 由事件流（类型 + 成败 + 时序）**纯函数重算**，延续「strength 不手存」原则（D2）；append-only 兼容——「复习重置遗忘曲线」（D7 抢救）= 追加一条高分新事件，旧事件不动，新事件自带更大的 S 与更新的 t_i，节点状态自然回到冷却期。

## 4. 节点遗忘状态机（D7 三阶段的数值化）

对枝叶节点 / 卫星，取该节点**最近一次有效接触事件**的稳定度 `S_last`（按 §3 含链增益），令 `Δt = now − t_last`：

| 阶段 | 条件 | 映射到现有实现 |
|---|---|---|
| 冷却保护 | Δt ≤ 1.0 × S_last | 遗忘冻结（protectionSeconds ← S_last，秒换算） |
| 断链离散（Dying） | 1.0 S < Δt ≤ 3.0 S | 剪边、漩涡、呼吸；forget 进度 = (Δt − S) / (2S) ∈ (0,1] |
| 坠入核心 | Δt > 3.0 S | 投影消失，数据保留（非物理删除） |

- 断链时刻保持率 R = e⁻¹ ≈ 0.37；坠入时刻 R = e⁻³ ≈ 0.05——「还剩三成记忆时开始报警，剩 5% 时落洞」。
- 点击抢救（D7）= 走复习流程 → 追加 delayed_retrieval / correction 事件 → S_last 更新且链增益 +1 → 自动回冷却期，无需特判。

## 5. 节点视觉强度（D9 权重模型）

与状态机**双轨并行**，职责分开：

```
strength(node, t) = Σ_i eff_i × R_i(t)     （涌现值，不手存）
```

- strength 只做视觉权重与 LOD 配给（D9 / D10）：节点大小、亮度、星云聚合排序。
- 遗忘状态机（§4）只用 S_last 时间口径——避免「多条弱事件堆出来的 strength」让该忘的内容赖在图上（强度可加、遗忘不可摊）。
- 骨架判定（D8）与 strength 无关，按层级深度 / 出度 / 强证据数独立计算。

## 6. 与 BlackHoleGraphEngine 占位参数的映射

| 占位参数 | 现状 | 替换口径 |
|---|---|---|
| protectionSeconds = 90f | 演示值（秒） | ← S_last × 86400（引擎 timeScale 演示模式继续允许加速） |
| forgettingFullSeconds = 240f | 演示值 | ← 3 × S_last × 86400 |
| absorbForgetGate = 0.85f | 兜底门限 | 删除，由状态机 Δt > 3S 判定 |
| decayOrbitBoost = 1.6f / decayRadiusPow = 0.8f | 视觉 | **保留**，不属记忆算法 |

## 7. 待拍板数值（默认值即推荐值）

1. S_0 系数 24 天、指数 2（§3.1 表）；
2. 复习增益 G = 2.0、封顶 S_max = 180 天；
3. lapse 惩罚系数 2（§3.3）；
4. 断链 1.0S / 坠入 3.0S 两档阈值（§4）。

任一数值不合适，改常量即可——函数结构（指数衰减 + 复习链倍增）不建议变。

## 8. 落地与验证计划（拍板后）

1. core:domain 新增 `ForgettingCurve` 纯函数对象（computeS(events) / nodeStage(events, now)），无 Android 依赖；
2. 单测：数值表断言（§3.1 五行）、复习链序列、lapse 回退、三阶段边界（1.0S / 3.0S ±ε）、append-only 抢救语义；
3. GraphRepository 接线：Node.strength 改由 §5 计算，engine 的 forget 输入改由 §4 状态机供给；
4. 模拟器 E2E：timeScale 加速下观察冷却→断链→坠入全旅程与抢救回归；
5. 更新本文件状态为「已定稿」并回写决策文档未决项。

---

## R103 落地记录（2026-09-29，已定稿并落代码）

- 状态：§7 默认数值已全部写入代码，单元测试全绿，图谱引擎已接线。
- 实现：
  - `core/domain` 新增 `ForgettingCurve.kt`（纯函数 stabilityDays / stage / strength，常量与证据效能表），配套 `ForgettingCurveTest.kt` 17 条数值单测。
  - `feature/memory` 新增 `GraphForgettingProjection.kt`（LearningFactReceipt 账本 → 节点级 BlackHoleForgetProfile 投影），配套 `GraphForgettingProjectionTest.kt` 6 条单测。
  - `BlackHoleGraphEngine.kt`：节点级遗忘档案（protection / forgettingFull / 远期预扣 elapsedProtectionSeconds / initialForget），无档案节点沿用 physics 全局时长（R82 语义：无证据不遗忘）。
  - `BlackHoleGraphScreen.kt` / `ContextHubScreen.kt`（GlobalGraphRoute）/ `AppShell.kt` / `HybridAppGraph.kt`：账本→投影→引擎全链接线；图谱重建时远期遗忘进度不重置。
- 测试：core:domain ForgettingCurveTest 17/17；feature:memory BlackHoleGraphEngineTest 39/39（含 7 条节点级档案新测）、GraphForgettingProjectionTest 6/6；两模块全部单测 0 失败；:app 与 :app-graphtest 编译通过。
- 待验证：模拟器 E2E（c9 保护 4s / 衰减 6s 演示链路 + 呼吸期点击抢救 toast）与真实学习数据联调，留待下一轮。
