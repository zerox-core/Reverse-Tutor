# Reverse Tutor · 本地审计结论与落地计划表（修订版 v2.1）

> 审计时间：2026-08-13｜v1 修订：2026-08-14｜v2 修订：2026-08-14（按用户对 v1 的三点异议与术语修正重排）｜v2.1 修订：2026-08-14（四处精修：B 序重排 / 文件级所有权 / 护栏可自动运行 / Wave 3 依赖序）｜审计方式：直连本地 `F:\xw\reverse-tutor`（feishu_mcp 实地核对），非 GitHub 侧推断｜分支：Android，工作树干净

## 〇、修订说明（v2.1，2026-08-14）

v2 确立了「契约优先、双轨并行、P3 前置」的主线并修正了 v1 的三处不严谨表述，用户认可其作为后续执行的主方案。v2.1 在把 v2 定为正式执行依据、同步飞书前，再修正四处，使仓库计划与飞书架构文档共同成为一致的权威来源。

### v2 的核心表述（不变）

**契约可验证且有边界护栏 → 前后端可以受控并行 → 每个垂直切片持续集成 → 最后再做全量替换验证。**

契约层定锁不等于「文档写完」：文档无法阻止 Feature 直接引入 core:data，也不能证明 UI 调到的 Repository 方法语义正确。可验证的契约 = 拓扑文档 + Gradle 依赖护栏 + 静态导入检查 + 消费者契约测试，四者齐备才构成并行开发的准入门槛。

### v2 对 v1 的三点修正（不变）

1. **「Wave 1 文档齐全后前后端可完全并行」不够严谨** → 改为「契约可验证且有边界护栏后，前后端受控并行」。护栏包括：Gradle 模块依赖检查、Feature/App 禁止导入 DAO/Entity/Database/SecretStore 的静态检查、消费者契约测试。
2. **Track A 与 Track B 的文件所有权有重叠** → 重新划分。`feature:*` 同时包含状态、路由和 Compose 页面，是必然冲突热点，不能整体归后端轨道；`app/shell` 包含装配和导航行为，也不应默认归前端。新分工见「三、双轨正确分工」。
3. **Wave 3 不应成为第一次集成** → 改为「每完成一条最小垂直切片，就有一次集成门禁」。等两条轨道都做完再集成，冲突和接口误解会集中爆发。原 Wave 3 的职责收缩为：双机矩阵、PWA parity、替换级验收。

### v2.1 对 v2 的四处修正

1. **B0–B6 表格顺序与「排序说明」自相矛盾** → 表格原列 B3→B4→B5，但排序说明要求 B4/B5 先定、再做 B3 的 Route 验收标准。v2.1 把表格重排为 **B0→B1→B2→B4→B5→B3→B6**，使表格与排序说明一致；B3 定位为「Route↔LEG 映射及验收标准」，在护栏与测试矩阵确定后补验收证据与门禁。
2. **Track A/B 所有权落到物理文件级** → v2 只说「靠共享接口和集成门禁管理，不靠文件独占」，但很多 Feature 文件把状态、事件和 Composable 放在同一文件，两条轨道同时改同一个文件必然冲突。v2.1 要求 Wave 0 的 A4 产出**文件路径 → 唯一责任人 → 可修改类型 → 共享接口 → 集成负责人**的物理文件所有权表；无法立即拆分的混合文件先标为 **hotspot**，指定单一临时 owner，两条轨道不得同时修改同一文件。
3. **B4 护栏必须可自动运行，不只是规则** → v2.1 把 B4 完成标准明确为四条可自动执行的检查（见 Wave 1 表后「B4 完成标准」），并要求 P1–P7 按风险建立实际验证、而非每条机械一个空测试，扩展现有 `HybridRepositoryContractTest`、`SchemaPolicyTest`。
4. **Wave 3 恢复内部依赖序** → Source 并非完全独立于 Memory/Graph：资料 chunk 最终进入 Context Evidence，图谱和记忆共享模型与持久化语义。v2.1 把 Wave 3 改为「Source 基础能力 → Memory/Graph/Context Evidence → 导入导出可并行 → Online Sync 在 schema、幂等和实体白名单稳定后开始」。同时 Wave 2B 改名为「差距审计与可靠性闭环」，因 P3-001 在 coverage registry 中已有 WorkManager 与持久化 job 基础，是补差距而非重建。

### v2 的视觉策略（不变，更明确）

视觉不应阻塞业务主线。前端**现在就可以**做纯视觉探索、页面布局和组件设计；但涉及业务状态、导航编排或调用能力的代码，应等对应契约通过「可验证定锁」后接入。

### 术语修正（必须执行）

架构文档中 **P5 是导入导出协议**。Route A/B/E 中写的「P5 Coordinator」是错误编号，应改为 **core:domain 编排层**，或明确编号为 **P2 的 Domain Coordination**。契约拓扑落仓时统一术语，禁止继续混用。

---

## 一、审计结论：三份历史产出的对错与口径偏差

前任 agent 的浏览报告、四阶段路线图、契约拓扑文档质量是递进的，方向认同。本轮实地复核了本地仓库，确认以下要点。

### 1. 工作树与分支：干净，但 worktree 积压是治理债

- Android 分支工作树干净（0 dirty），HEAD 与任务描述 `9625298` 一致。
- `F:\xw\worktrees\` 下积压 **13 个 worktree**。抽查两个分支干净，但是否已合并、可否归档删除需要用户确认——长期不清理会让「哪个分支是真值」变模糊。

### 2. index.html 体积漂移：文档落后实际 235KB

- 实测 `static/app/index.html` = **676 KB**，`AGENTS.md` 仍写「~441KB」。文档落后实际 235KB，「双实现漂移治理」负担被低估。建议更正 AGENTS.md 数字。

### 3. 契约层已有基础但未闭环

freeze 文档（`native-backend-protocol-data-contract-freeze.md`，2026-07-04）已定义冻结边界：

- **冻结层**（后端协议接口层 + 数据层）：`core/model`、`core/protocol`、`core/llm`、`core/data/*Repository`、Room schema、SecretStore
- **未冻结层**（前端 UI 设计层）：`app/.../theme`、`app/.../ui`、`feature/*` 的 Compose 页面

freeze 文档管的是「不许动什么」。但**契约拓扑文档（P1–P7 协议边界 + Route A–F 开发路线）还躺在飞书云文档里**，仓库 `tasks/` 没有对应文件——「怎么跨边界协作」这层契约没有进仓库，两轮迭代内必与代码漂移。

**v2 补充**：即使拓扑落仓，文档本身也无法阻止越界依赖。仓库已有 `HybridRepositoryContractTest`、`SchemaPolicyTest` 等契约测试基础，应在此基础上扩展护栏与测试矩阵，而不是另建平行体系。

### 4. Phase 12 视觉暂停状态

`progress.md` 记录 Phase 12 处于「collecting feedback; implementation intentionally paused」，已确认 8+ 项视觉/交互缺陷（清单见附录 A）。这些缺陷属于前端表现层轨道，不阻塞契约定锁和后端开发；前端可在契约未定锁期间先行做纯视觉探索。

### 5. P3 排序分歧与发布门禁基线

- coverage registry 仍是 `verified=4 / in_progress=38 / not_started=2 / 28 P0 blocker`，与 next-workplan 的 gate snapshot 一致。
- P3-001 的持久化 job 地基已进代码（registry 行有证据）。LEG-016/017 属 P0 数据隔离风险。**P3 提前到后端第一梯队**（Wave 2B），定位为「差距审计与可靠性闭环」而非重建。
- next-workplan 的「Recommended Next Action」指向 UX-005/P5-008，与 Phase 12 暂停状态矛盾，需更新（Wave 1 的 B6 统一处理）。

---

## 二、认定方案（v2.1）

### 核心原则

1. **契约可验证且有边界护栏是关键路径**。契约 = 拓扑文档 + Gradle 依赖护栏 + 静态导入检查 + 消费者契约测试。四者齐备，前后端才可受控并行。
2. **受控并行，不是完全独立**。Track A（领域能力与状态流）与 Track B（表现层与交互）按 UiState/UiEvent/Facade/Coordinator 输入输出对接；每条切片先定义共享接口再各自实现。
3. **垂直切片持续集成**。每完成一条最小垂直切片就有一次集成门禁，不积压到后期集中爆发。
4. **视觉不阻塞业务主线**。纯视觉探索现在就能做；业务状态、导航编排、调用能力的代码等契约定锁后接入。
5. **P3 提前**：数据隔离（deleted-session 不写消息、stale-token 拒绝）优先于导入导出 parity。
6. **冻结层变更走门禁**：P3-001、真实 LLM、Room migration、core:llm 都在当前冻结范围内——不能因为它们被排进 Wave 2 就默认允许修改。每个切片开始前仍要走一次「变更说明 + 测试方案 + 用户批准」。
7. **内测 APK 先行，PWA 仍是生产线**：直到 Wave 4 全量验收 + 用户明确批准才退出 PWA。

---

## 三、双轨正确分工

### Track A · 领域能力与状态流

- `core:domain` / `core:data` / `core:llm` / `core:remote`
- Coordinator、Repository 实现、Worker、迁移、Provider runtime
- Feature 的 state、event、facade、ViewModel

### Track B · 表现层与交互

- Compose Screen、可复用 UI、theme、图标、纯导航表现
- 只消费不可变 UI state 和 event/facade
- **不直接接触** DAO、Entity、Database、SecretStore 或协议 DTO

### 共享接口与协作流程

- 每条切片先定义 **UiState / UiEvent / Facade 或 Coordinator 输入输出**，双方据此各自实现。
- Track B 缺能力时提交 **capability request**（能力缺口申请）。
- Track A 先补契约测试和变更说明，再按冻结流程申请修改。

这比「Repository 接口对接」更完整：Repository 是数据能力入口，但聊天生成、同步、后台任务这类多步骤业务需要 Coordinator/Facade 编排；否则 UI 很容易把多次 Repository 调用拼回页面层，重新产生耦合。

### 文件所有权要点（物理文件级，v2.1 强化）

- **所有权表必须落到物理文件**：Wave 0 的 A4 产出「文件路径 → 唯一责任人 → 可修改类型 → 共享接口 → 集成负责人」五行结构表。`feature:*` 的 state/event/facade/ViewModel 归 Track A、Compose Screen 归 Track B 方向正确，但当前很多 Feature 文件把状态、事件和 Composable 放在同一文件，必须按文件逐一归属。
- **混合文件标为 hotspot**：无法立即按轨拆分的混合文件（同文件内既有 state/event 又有 Composable），先标为 **hotspot**，指定**单一临时 owner**（Track A 或 Track B 之一），另一轨不得直接修改。如需跨轨改动，先走拆分（提取 state/Composable 到独立文件）再各自认领。
- **两条轨道不得同时改同一文件**：这是硬约束，靠物理文件所有权表 + 分支/合并规则保证，不靠口头约定。
- `app/shell` 包含装配和导航行为，不默认归前端：装配逻辑归 Track A，纯导航表现归 Track B，按文件内职责同样落到文件级。

---

## 四、计划表（v2.1）

### Wave 0 · 基线

| 编号 | 事项 | 落点 | 谁来做 |
|-|-|-|-|
| A1 | 修正文档事实：AGENTS.md 的 index.html 体积更正为 676KB；记录 Android HEAD | `AGENTS.md` | codex/agent |
| A2 | 跑现有模块测试，确认基线全绿 | JVM 单测 | codex/agent |
| A3 | 明确冻结层改动的审批边界（冻结层清单 + 变更说明模板） | freeze 文档 | codex/agent |
| A4 | **建立物理文件所有权表和分支/合并规则**（按第三节双轨分工）。产出：文件路径 → 唯一责任人 → 可修改类型 → 共享接口 → 集成负责人；无法立即拆分的混合文件标为 hotspot 并指定单一临时 owner；两条轨道不得同时改同一文件 | `tasks/` | codex/agent |
| A5 | worktree 盘点 + 归档方案（**不删，先列清单等确认**） | `F:\xw\worktrees\` | codex 列清单，用户拍板 |

### Wave 1 · 可验证契约定锁（并行开发的真正准入门槛）

| 顺序 | 任务 | 目的 |
|-|-|-|
| B0 | **架构裁决记录** | 统一 P1–P7 术语（含「P5 Coordinator」→ core:domain 编排层 / P2 Domain Coordination 的修正）、Coordinator 归属、冻结层变更审批和 API 缺口流程 |
| B1 | **代码事实盘点** | 先从源码提取 Repository、Gateway、Transport、消费者和实现者清单，避免文档手写接口漂移 |
| B2 | **契约拓扑落仓** | 文档记录契约 ID、权威源码路径、行为不变量、消费者、测试位置；精确签名仍以 Kotlin 源码为准。落 `tasks/native-contract-topology.md` |
| B4 | **边界护栏（可自动运行）** | Gradle 模块依赖白名单检查；Feature/App 源码直接导入 DAO/Entity/Database/SecretStore 为 0；全量 JVM 测试入口执行这些检查；CI 或本地标准命令失败时阻断合并 |
| B5 | **契约测试矩阵** | P1 模型兼容、P2 Repository 实现、P3 Provider fixture、P4 网络错误/幂等、P5 schema/脱敏、P6 migration、P7 token 使用规则。扩展仓库已有 `HybridRepositoryContractTest`、`SchemaPolicyTest`，不另建平行体系 |
| B3 | **Route↔LEG 映射及验收标准** | 在 B4 护栏与 B5 测试矩阵确定后，为每条 Route 写清范围、P0/P1 行、验收证据、依赖和风险，并把护栏/测试作为 Route 的验收门禁 |
| B6 | **运行手册同步** | 最后更新 `AGENTS.md`、`native-android-next-workplan.md` 和所有权表 |

**排序说明**：B1/B2 先做代码事实盘点再落拓扑（事实先于文档）；**B4 护栏与 B5 测试矩阵先定，再做 B3 的 Route 映射验收标准**——表格顺序已与此一致（B4→B5→B3）；B5 不是「测试骨架」，是「可执行的架构护栏 + 首批高风险契约测试」；B6 收尾同步运行手册。

**B4 完成标准（可自动运行，v2.1 明确）**：

1. **Gradle 模块依赖白名单检查**：在构建脚本中配置模块依赖白名单，`feature:*` / `app` 不得直接依赖 `core:data` 的 DAO/Entity/Database 包、`SecretStore`；越界依赖令构建失败。
2. **静态导入检查**：Feature/App 源码中直接 `import` DAO、Entity、Database、SecretStore 的数量为 **0**，由静态检查脚本或 lint 规则强制。
3. **全量 JVM 测试入口执行这些检查**：`./gradlew test`（或等价标准命令）的执行路径中包含上述依赖白名单与导入检查，跑测试即跑护栏。
4. **CI 或本地标准命令失败时阻断合并**：护栏失败 = 合并阻断，不可绕过、不可降级为 warning。
5. **P1–P7 按风险建立实际验证**：不机械地「每条一个空测试」，而是按各协议的真实风险（如 P3 的 deleted-session 隔离、P5 的脱敏、P6 的 migration 回滚）建立有实际断言的测试；现有 `HybridRepositoryContractTest`、`SchemaPolicyTest` 适合扩展为承载点。

**Wave 1 完成标志 = 契约发布包**：契约拓扑 + LEG 映射 + 依赖护栏 + 测试矩阵 + 所有权表，全部落仓库。这是并行开发的真正准入门槛。

### Wave 2A · 核心教学闭环

- 先用 Fake Runtime 跑通 Session → 配置 → 消息 → 回复 → 重启 → 隔离。
- **每步加入设备 smoke，不等到末尾。**

### Wave 2B · 差距审计与可靠性闭环

- **定位是「差距审计与可靠性闭环」，不是重建**：P3-001 在 coverage registry 中已有 WorkManager 与持久化 job 基础，Wave 2B 是补齐剩余的数据隔离、恢复和取消证明（deleted-session 不写消息、stale-token 拒绝、session 切换不串），而非从零建设后台任务。
- 再接真实 Provider transport、流式解析、失败映射（LEG-016/042）。
- 生命周期稳定后才做通知与后台设置（LEG-017 通知部分）。
- **仍属冻结范围**：真实 LLM、Room migration、core:llm 的改动逐切片走「变更说明 + 测试方案 + 用户批准」门禁，不因排进 Wave 2B 而豁免。

### Wave 2C · 前端并行

- Track B 按已发布的 UiState/Facade 接入页面。
- **每个 UI 切片与对应能力切片同步集成**——每完成一条最小垂直切片过一次集成门禁，不积压到 Wave 3。
- 纯视觉探索、页面布局、组件设计可在 Wave 1 期间提前开始（不触碰业务状态/导航编排/调用能力）。

### Wave 3 · 独立业务切片（恢复内部依赖序，v2.1 修正）

Source 并非完全独立于 Memory/Graph：资料 chunk 最终会进入 Context Evidence，图谱和记忆也共享模型与持久化语义。内部依赖序如下：

1. **Source 基础能力**（资料接入、chunk、解析）先行；
2. **Memory / Graph / Context Evidence** 依赖 Source 产出，在其后推进；
3. **导入导出**可与上一阶段并行（协议层 P5 已冻结，不依赖 Source 运行时）；
4. **Online Sync** 在 schema、幂等规则和实体白名单稳定后开始，不抢跑。

### Wave 4 · 替换级验证

- LEG P0 逐项 verified/waived（28 条全量审计）。
- 双设备矩阵（HMA-AL00 + Mate 60）、迁移、导入导出、回归、签名和 runbook。
- PWA parity 确认后，**由用户批准退出 PWA**。

### 依赖关系

```
Wave 0 (基线)
  └→ Wave 1 (可验证契约定锁 B0–B6) ← 并行准入门槛
       ├→ Wave 2A (核心教学闭环, Fake Runtime) ─┐
       ├→ Wave 2B (差距审计与可靠性闭环) ────────┤ 每条垂直切片
       ├→ Wave 2C (前端并行, 同步集成) ──────────┤ 过一次集成门禁
       └→ Wave 3 (独立业务切片, 有内部依赖序) ──┘
            └→ Wave 4 (替换级验证 → 用户批准退出 PWA)
```

---

## 五、不可逆约束（全程铁律）

- **冻结层不动**：`core/model`、`core/protocol`、`core/llm`、`core/data/*Repository`、Room schema/migration、`SecretStore`。UI/feature 只能走 Repository facade / Coordinator，不得直碰 DAO/Entity/SQL/Keystore/协议 DTO。
- **冻结范围覆盖 Wave 2 内容**：P3-001、真实 LLM、Room migration、core:llm 都在冻结范围内。每个切片开始前走「变更说明 + 测试方案 + 用户批准」门禁，不因排期而豁免。
- **签名铁律**：`release.jks` + alias `reverse-tutor` + `applicationId` 一律不动。
- **PWA 退出**：必须 Wave 4 全量验收 + 用户明确批准，在此之前 PWA 仍是生产线。
- **环境**：Windows + PowerShell；Python 只用 `py`；不用 `cd`/`git push`/tag。
- **改行为先补测试**：不许删/弱化已有测试让它过；`py -m pytest -q --ignore=tests/test_project_homepage.py` 必须绿。
- **worktree 清理属不可逆批量操作**：先列清单等用户确认，不自作主张删。
- **capability request 流程**：Track B 发现业务能力缺口时提交能力缺口申请；Track A 先补契约测试和变更说明，再按冻结流程申请修改（freeze 文档已定义底层规则）。
- **物理文件所有权**：两条轨道不得同时修改同一文件；混合文件先标 hotspot、指定单一临时 owner，需跨轨改动先拆分。

---

## 六、codex / agent 能做什么 / 需要用户配合什么

- **codex/agent 能直接做**：代码修改、JVM 单测、读文件、git 状态核对、写 tasks/ 文档、更新 AGENTS.md、契约拓扑文档撰写、护栏与契约测试编写。
- **需要用户本地配合**：构建（`./gradlew`）、设备验证（HMA-AL00 / Mate 60 截图）、`py -m pytest` 基线、签名打包。
- **前端表现层**：由用户主导（Track B），codex/agent 不介入除非用户要求。
- **建议立即开始**：Wave 0 + Wave 1（基线 + 可验证契约定锁），这是受控并行的准入门槛。

---

## 附录 A · Phase 12 视觉缺陷清单（Track B 参考，不阻塞关键路径）

以下缺陷由 `progress.md` 记录，供前端表现层开发时参考。节奏由用户自定，不在后端关键路径上。

1. 颜色饱和度过低（视觉过淡）
2. 详情页字号过小
3. 预设身份卡尺寸过大
4. 故事插画不可滑动
5. 自定义世界树行不打开编辑器
6. 全局图谱手势冲突（全屏 canvas 吞手势 + HorizontalPager 失效）
7. 聊天 header 入口错连 context hub（应指向活动 session 设置）
8. overflow 按钮无效
9. 38dp 发送按钮挤在 48dp composer 内（间距不足）
10. spatial 指示器常驻（应瞬时化）
11. 公共兴趣卡无点击反馈
12. session 行缺头像框
13. 缺长按菜单（重命名/置顶/导出/删除）
14. 首页 challenge 分页阈值低/回弹

## 附录 B · 契约层现状摘要

### 已冻结（freeze 文档，2026-07-04）

| 层 | 模块 | 状态 |
|-|-|-|
| 后端协议接口层 | `core/model`（14 模型文件）、`core/protocol`（7 版本化 schema）、`core/llm`（三阶段+三 Provider）、`core/data/*Repository`（12 个 Repository） | 已冻结 |
| 数据库/数据层 | `core/data/local`（Room schema 三分区）、`core/data/preferences`、`SecretStore.kt` | 已冻结 |

### 未冻结（可自由重构）

| 层 | 模块 | 状态 |
|-|-|-|
| 前端 UI 设计层 | `app/.../theme`、`app/.../ui`、`feature/*` 的 Compose 页面 | 未冻结，用户主导 |

### 待落仓库（Wave 1 产出）

| 产出 | 当前位置 | 目标位置 | 对应任务 |
|-|-|-|-|
| 架构裁决记录（术语统一 + Coordinator 归属 + 审批流程） | 不存在 | `tasks/` | B0 |
| 代码事实盘点（Repository/Gateway/Transport/消费者/实现者） | 源码中 | `tasks/` | B1 |
| 契约拓扑文档（P1–P7 + Route A–F） | 飞书云文档 | `tasks/native-contract-topology.md` | B2 |
| 边界护栏（Gradle 依赖白名单 + 静态导入检查 + CI/JVM 入口，可自动运行） | 不存在 | 构建脚本/CI | B4 |
| 契约测试矩阵（P1–P7，按风险建立实际验证，扩展现有测试） | 部分存在（HybridRepositoryContractTest、SchemaPolicyTest） | `mobile-native/` test | B5 |
| Route↔LEG 映射表（范围/验收证据/依赖/风险，含护栏门禁） | 不存在 | 同拓扑文档内 | B3 |
| AGENTS.md 分工声明 + next-workplan 更新 + 所有权表 | 不存在 | `AGENTS.md` / `tasks/` | B6 |
