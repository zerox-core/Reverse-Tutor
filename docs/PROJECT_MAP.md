# Reverse Tutor 项目地图

> 生成日期：2026-10-14
> 用途：一次看懂仓库真实形状（人读 + Agent 读）
> 数据来源：仓库实测（文件/行数、测试输出、git 记录），非回忆

---

## 1. 这是什么项目

**用「学生反过来教 AI」检验真理解**：用户当老师讲解，AI 扮演一个真实水平的学生——会卡壳、会装懂、会追问。教得清楚，学生就懂；讲得含糊，学生当场暴露。

产品主线只有一条（`docs/PROJECT_DEVELOPMENT_MAINLINE.md`）：

```
用户可用的普通会话
  → 上下文与学习算法
  → 结构化记忆拓扑
  → 子分支会话
  → heartbeat 主动对话
  → 学习事实与图谱投影
  → 最终视觉与副屏体验
```

---

## 2. 核心资产：反转家教算法（Web → Android 移植）

**本项目的专长就是这套算法，不是通用聊天应用。** 它的血统是一条独立的移植链：

```
F:\xw\reverse-tutor-web-algo-spike          ← 算法源头（独立 repo，不在主仓库内）
  Python + FastAPI + 20KB 页面（纯算法/架构验证 Spike，不依赖线上服务）
  31 个结构化工具 · 39 文件 / 5347 行 · 13 个测试文件（158 passed / 4 failed*）
  app/engine/orchestrator.py   LLM 自主决策循环
  app/engine/student_card.py   学生角色卡
  app/tools/*.py               10 个工具模块（31 工具）
  app/tools/registry.py        REGISTRY
        ↓  移植（只 import 冻结区，不修改冻结区）
mobile-native/feature:reversetutor          ← Android 落地（目标）
  31 个确定性工具（2026-10-14 全量对齐 spike）· 主源码 17 文件 / 5.7k 行 · 测试 10 文件 / 2.4k 行
        ↓  对拍验收
  conformance 测试：同输入 → 同输出（PortConformanceTest / GraphScoreConformanceTest…）
```

映射表：[reversetutor-algo-port-map.md](F:\xw\reverse-tutor\mobile-native\docs\reversetutor-algo-port-map.md)（spike → Kotlin 逐项对位）

\* spike 的 4 个失败为**真实 LLM / 网络依赖**用例（`test_llm_single_round` 2 个、`test_retrieval` websearch 2 个），非算法缺陷。

> ⚠️ **易混淆点**：主仓库里的 Python（`server.py` / `engine.py` / `db.py`）**不是**算法的 Web 端来源——那是**在线服务（产品后端）**。算法的 Web 来源是上面那个独立 spike 仓库。`AGENTS.md §0` 只写了主仓库三条线，**没有提到 spike**，据此判断会漏掉算法的真正源头。

---

## 2.1 主仓库的代码分布

> **范围指令（2026-10-14，用户）：不再投入旧 PWA 相关开发。**
> 含义：`static/app/index.html`、`mobile/`（Capacitor 壳）不再是开发对象，也不作为行为参考。
> 边界：**不删除、不清理、不宣布 PWA 退出**（退出仍需 Phase 6 + 用户明确批准）；只是不再为其分配任何工作量。新工作一律落在 native 或算法线。

| # | 实现面 | 规模 | 入口 | 定位 |
|---|---|---|---|---|
| 1 | **Python 后端 + 在线服务** | 176 文件 / 31.7k 行 | `server.py`（FastAPI） | 在线能力：挑战活动、内容、同步、鉴权、统计。**测试主力在这**（`tests/`） |
| 2 | **PWA / Capacitor** | `static/app/index.html` **765 KB 单文件** | 由 1 托管 | ⛔ **已停止投入**（见上方范围指令）。历史产物，保留不动 |
| 3 | **Native Android** | `mobile-native/` 707 个 .kt / **143.5k 行** / 14 模块 | `app` | **当前产品方向**。反转家教算法线见 §2、§4；在线能力客户端在 `core:remote` |

关系图：

```
static/app/index.html ──(Capacitor 打包，历史路径)──→ mobile/ 安卓壳
        ↑ 托管
server.py ── adapters/online/ ── online_db/        ← 在线服务（挑战/内容/同步/统计）
        ↑ HTTP
mobile-native/core:remote ──→ mobile-native/app    ← 现行 native 客户端
```

> ⚠️ **不要把三条混成一条**。改 bug 前先判断目标在后端、PWA，还是 `mobile-native/`。

---

## 3. Native 模块地图（14 个 Gradle 模块）

| 模块 | 职责 |
|---|---|
| `app` | 应用装配与壳：`MainActivity`、`shell/`（导航/路由）、`wiring/`（`HybridAppGraph` 依赖装配） |
| `core:model` | 共享领域模型（含 `LlmProfile` 等） |
| `core:protocol` | 导入导出协议与版本化 schema |
| `core:design` | 设计系统（主题/组件） |
| `core:domain` | 领域契约：`OnlineContentContracts`、`OnlineInsightContracts`、`GlobalProjectionContracts`、`InitiativeContracts`、`WindowMemoryContracts` |
| `core:data` | Room 数据库、各 `*Repository` 实现、`online/` 适配、`background/` 后台生成 |
| `core:llm` | LLM Provider HTTP 传输（`ProviderHttpTransport` / `UrlConnectionProviderHttpTransport` / `ProductionLlmGenerationRuntime`） |
| `core:remote` | **在线服务客户端**：`OnlineApi`/`HttpOnlineApi`、`AuthApi`、`OnlineCapabilities`、`OnlineAuthSessionManager`、传输层 |
| `feature:chat` | 聊天主页/会话/独白抽屉/引导卡/新会话预设 |
| `feature:memory` | 记忆与周报看板 |
| `feature:sources` | 资料源 |
| `feature:settings` | 设置 |
| `feature:reversetutor` | **反转家教算法内核**（见 §4） |
| `app-graphtest` | 知识图谱实验壳 |

---

## 4. 反转家教算法线：`feature:reversetutor`

**定位（2026-10-14 用户确认）：算法是本源与专长——由 Web 端 Spike 移植到 Android，是基底引擎；学习场景是配置数据。**

规模：主源码 17 文件 / 5628 行；测试 10 文件 / 2558 行。工具覆盖：**31/31**（2026-10-14 全量对齐 spike）。

| 文件 | 行 | 作用 |
|---|---:|---|
| `tools/CoreTools.kt` | 2290 | 31 个确定性工具的实现（事实层） |
| `loop/ToolLoop.kt` | 1025 | 多轮自主决策执行引擎 + 中文比例闸 + 每轮会话快照 |
| `knowledge/TopicKnowledgeBase.kt` | 348 | 主题知识库 |
| `tools/ToolBase.kt` | 317 | 工具契约、`ToolContext`、`ToolResult` |
| `curriculum/CurriculumCatalog.kt` | 296 | 课标目录（学段/先修/状态推断） |
| `memory/RTMemoryStore.kt` | 209 | **长期记忆端口 + 进程内实现（2026-10-14 新增）** |
| `retrieval/MaterialStore.kt` | 187 | 学生资料库内存检索（`search_materials` 的后端） |
| `prompt/PromptAssembler.kt` | 164 | 系统提示词组装 |
| `card/StudentCards.kt` | 155 | 学生角色卡 + 状态选桶 |
| `score/ScoreEngine.kt` | 127 | 质量评估/教学失败检测 |
| `graph/KnowledgeGraphBuilder.kt` | 127 | 知识图谱节点与先修边 |
| `tools/ToolRegistry.kt` | 119 | 工具注册表 |
| `guard/MathGuard.kt` | 87 | 数学红线（正则，无 sympy） |
| `scenario/ScenarioPackage.kt` | 82 | 场景包加载与素材注入（2026-10-14 新增） |
| `session/RTSession.kt` | 53 | 会话状态 |
| `retrieval/RTWebSearchProvider.kt` | 30 | **外部检索端口（2026-10-14 新增）** |
| `core/PyCompat.kt` | 21 | 与 Python 端对齐的数值语义 |

### 数据流

```
ReverseTutorTurnPreparationCoordinator        (app/wiring/session, 后台回合)
  → ToolLoop.runTurn(session, history, …)     (决策循环, maxSteps 12)
      ⇄ ToolLoopLlmClient                     (Fake 脚本化 / OpenAI tool-calling)
      ⇄ 31 工具（lookup_curriculum / search_materials / …）
      输出 onEvent：llm_round / llm_thinking / tool_*
  → GenerationPartialStore.setMonologue(…)    (抽屉数据源, UI 150ms 轮询)
  → MessageRepository（落库 monologue 字段）
```

### 集成分期（R-RT 系列，10-11 ~ 10-12 完成）

| 编号 | 内容 | 落点 |
|---|---|---|
| R-RT-1 | 会话级路由端口：反转会话走算法内核，其余走原 RAG/图谱链路 | `RoutingTurnPreparationPort.kt` |
| R-RT-2 | 运行时装配：从 assets 载课标/学生卡，构建 31 工具 + ToolLoop | `ReverseTutorRuntimeAssembly.kt` |
| R-RT-3 | 协调器接入 `HybridAppGraph`，LLM 客户端按运行模式分派 | `HybridAppGraph.kt` |
| R-RT-4 | 按会话路由分派 | `HybridAppGraph.kt` |
| R-RT-5 | 抽屉实时流：协调器写自己的 `partialStore` | `ChatScreen.kt` |
| R-RT-6 | 内置预设 `reverse-tutor-core` | `FormalPresetModels.kt` |

### 真流式（2026-10-13）

三处协作，缺一不可：

1. `ToolLoopLlmClient.chatStreaming`（**接口默认方法**，退化到 `chat`，既有 Fake 零改动）
2. `ReverseTutorOpenAiLlmClient` 的 SSE 实现：`stream`/`enable_thinking`/`include_usage`，头 `Accept: text/event-stream` + `Accept-Encoding: identity`（防 gzip 缓冲整段）；**必须调三参 `executeStreaming`**——接口的两参默认实现会退化成一次性请求；流式失败自动降级
3. 协调器双缓冲：`llm_thinking` 增量过 `isMostlyChinese`（汉字占比 ≥30%）闸后覆盖写抽屉；轮末定稿转正并清流式缓冲

---

## 5. 场景包管线（2026-10-14 新增，阶段 A 第一切片）

**目标**：一套引擎吃不同学习场景，换主题不改代码。

```
activities/<slug>/                                  ← 唯一真源（人工配置）
  ├─ student-card.yaml        学生卡（草案）
  ├─ session-template.md      会话模板
  ├─ rubric.yaml              五维最佳区间评分
  ├─ misconceptions.yaml      误解条目
  ├─ onboarding-d0.md         D0 起点测评
  ├─ days/day-01..07.md       逐日编排（front-matter）
  └─ sources/src-*.md         精选资料（front-matter + 正文）
        ↓  py -m scripts.build_scenario_packages   （复刻既有 build_challenge02_tasks 范式）
mobile-native/feature/reversetutor/src/main/assets/scenarios/<slug>.json   ← 冻结产物，勿手改
        ↓  ScenarioPackage.parse + installInto
MaterialStore.addDoc(…)                             ← 打通此前空缺的生产调用点
        ↓  装配期注入
ReverseTutorRuntimeAssembly.buildCoordinator
```

**已打通**：素材注入 → `search_materials` 在非数学场景（久坐/睡眠/膳食）可命中。
**缺口**：

| 缺口 | 说明 |
|---|---|
| 会话 ↔ 场景绑定 | 现为装配期装入全部打包场景；按会话/按场景隔离需要 `scenarioId` 落到会话 |
| 学生卡映射 | 活动包 yaml 与 `student_cards.json` 结构不兼容：yaml 缺 8 桶示例台词，且自述"注册机制待定、权重待定" |
| 课标场景化 | 引擎课标仍只有"高中数学"；challenge-02 包内无课标文件 |
| rubric 消费 | `rubric.yaml` 五维评分未被 `ScoreEngine` 读取 |
| 在线下发 | 场景包目前随 APK 打包，未从在线服务拉取 |
| 素材清洗 | `sources/*.md` 含网页抓取残留导航文字，源文件自述清洗属 I2 阶段 |

---

## 6. 在线服务能力（Python，已实现）

| 类别 | 端点 |
|---|---|
| 健康 | `/health` |
| 内容 | `/content/feed`、`/content/{slug}` |
| 活动 | `/activities`、`/activities/{activityId}`、`/join`、`/progress`、`/participation`（GET/POST）、`/leaderboard` |
| 同步 | `/sync/push`、`/sync/pull` |
| 洞察 | `/insights/weekly` |
| 版本 | `/app/releases/latest` |
| 鉴权 | 登录、`/refresh`、`/me`、`/sessions`、`/sessions/{id}` |
| 管理端 | 活动 CRUD、`publish`/`close`/`offline`、`stage-plan/generate`、`stages`、`probe-plan/generate` |

数据层：`online_db/`（活动/内容/鉴权/幂等/遗忘曲线 + challenge-01/02 冻结内容）；适配层：`adapters/online/`。

---

## 7. 测试基线

| 线 | 命令 | 基线（2026-10-14 实测） |
|---|---|---|
| Python | `py -m pytest -q --ignore=tests/test_project_homepage.py` | **735 passed, 28 skipped** |
| Android | `.\gradlew.bat :app:testDebugUnitTest :feature:reversetutor:testDebugUnitTest` | **541 tests, 0 failures** |

```powershell
# 仓库根
py -m pytest -q --ignore=tests/test_project_homepage.py
# mobile-native 目录
.\gradlew.bat :app:testDebugUnitTest :feature:reversetutor:testDebugUnitTest --console=plain
```

环境硬规则：Python 只用 `py`；PowerShell 不用 `cd`（用工具 cwd）；测试强制 mock LLM，不打真实网络。

---

## 8. 冻结区（只读消费，不得修改）

```
mobile-native/core/model
mobile-native/core/protocol
mobile-native/core/llm
mobile-native/core/data/*Repository
mobile-native/core/data/local
mobile-native/core/data/preferences
mobile-native/core/data/llm/SecretStore.kt
Room schema 导出与迁移
```

UI/feature 只能经 Repository、domain model、protocol facade 调业务能力；不得直接碰 Room DAO/Entity/SQL/Keystore。
**核验命令**（应为空）：

```powershell
git show --name-only --format='' <commit> | Select-String 'core/(model|protocol|llm)/|Repository|SecretStore|Room|schema'
```

---

## 9. 分支约定（2026-10-14 订正）

- **`main` 是唯一生产实现分支**（原章程写 `newmp`，已过期）。
- `newmp` 已完全并入 `main`（`newmp` 领先 0 提交，`main` 领先 178），仅作旧行为参考。
- 不得把旧分支代码整体复制到主线；不得让 PWA/Capacitor 与 native 形成第三条产品线。
- 不 `git push`、不打 tag，除非用户明确要求。

---

## 10. 变更台账

### 已推送 `f0d8ac6`（2026-10-13，48 文件，+10759/−113）

按文件修改时间可分为四段（**时间戳取证，非回忆**）：

| 时间段 | 文件数 | 内容 |
|---|---:|---|
| 10-09 ~ 10-10 | 20 | **算法内核移植**：`feature:reversetutor` 模块骨架（ToolLoop/CoreTools/课标/学生卡/MaterialStore/MathGuard/ScoreEngine/图谱）、assets、模块接线、移植映射表 |
| 10-11 ~ 10-12 | 18 | **native 集成 R-RT-1..6**：路由端口、装配、`HybridAppGraph`、抽屉流、内置预设；以及提示词组装、图谱/工具契约测试、聊天 UI 分层、独白窗口 600→4000 |
| 10-13 上午 | 4 | `ReverseTutorContractMapper` + 其测试、端到端冒烟、协调器测试 |
| 10-13（本次流式） | 6 | `ToolLoop` 流式、SSE 客户端、协调器 `llm_thinking`、流式测试、`.gitignore` |

全局核验：**无文件删除**；**签名/版本号未动**；14 个测试文件为纯新增。
唯一被修改的既有测试：`FormalPresetModelsTest` 新增预设后同步期望集合（断言仍为精确相等）。

### 未提交（2026-10-14，场景切片 + 阶段 3 工具 + 章程订正）

| 文件 | 变更 |
|---|---|
| `scripts/build_scenario_packages.py` | +97 新增（生成器，含 `--check`） |
| `mobile-native/.../scenario/ScenarioPackage.kt` | +82 新增 |
| `mobile-native/.../assets/scenarios/challenge-02-gamer-health.json` | +93 生成（11 篇素材） |
| `mobile-native/.../ScenarioPackageTest.kt` | +130 新增 |
| `tests/test_scenario_packages.py` | +45 新增 |
| `mobile-native/.../memory/RTMemoryStore.kt` | +209 新增（长期记忆端口） |
| `mobile-native/.../retrieval/RTWebSearchProvider.kt` | +30 新增（外部检索端口） |
| `mobile-native/.../Stage3ToolsContractTest.kt` | +19 测试新增 |
| `mobile-native/.../tools/CoreTools.kt` | +360 四工具实现与注册（27 → 31） |
| `mobile-native/.../loop/ToolLoop.kt` | +67 注入 memoryStore / webSearchProvider（带默认值）+ 每轮写会话快照 |
| `mobile-native/.../ToolContractTest.kt` | 计数 27 → 31、分类对齐 spike、minArgs 补 4 项 |
| `mobile-native/.../ToolLoopTest.kt` | +3 测试（快照接线、跨会话聚合、无 store 兼容） |
| `ReverseTutorRuntimeAssembly.kt` | +34 场景注入 + 长期记忆装配 |
| `mobile-native/docs/reversetutor-algo-port-map.md` | 二批完成状态与遗留 |
| `docs/PROJECT_DEVELOPMENT_MAINLINE.md` | 生效分支 newmp → main（v1.1） |
| `docs/PROJECT_MAP.md` | 新增项目地图 |

---

## 11. 待决事项

1. **长期记忆跨进程持久化**：`RTMemoryStore` 现为进程内实现，App 重启后
   events / teaching_patterns / session_snapshots 全丢。接入 Room 需改
   `core/data/local` 与 Room schema（**冻结区**）→ 需用户明确批准。
2. ~~`save_snapshot` 的写入接线~~ **已完成（2026-10-14）**：`ToolLoop` 每轮结束写快照
   （对齐 spike session_service），失败静默不影响回合；跨会话聚合由
   `progressReportAggregatesMultipleSessionSnapshots` 锁定。
3. 学生卡映射规则（活动包 yaml ↔ native 卡 8 桶）
4. 会话 ↔ 场景绑定（按会话携带 `scenarioId`）
5. 场景包清洗（I2 阶段 chunker/FTS5）
6. 在线下发 + 使用统计（阶段 B）
7. 用户自配场景 / MCP 化（阶段 C）
8. `f0d8ac6` 是否拆分（需改写已推送历史，不推荐）
9. `f0d8ac6` 提交信息里的"31 工具注册表"在提交时**实为 27**（已于 2026-10-14 补齐至 31，故该描述现已成立）；
   历史提交信息不改，以本文档为准

---

## 12. 快速自查命令

```powershell
git -C F:\xw\reverse-tutor status --short          # 未提交改动
git -C F:\xw\reverse-tutor log --oneline -10       # 最近提交
git -C F:\xw\reverse-tutor show --stat <commit>    # 某次提交全貌
py -m scripts.build_scenario_packages --check      # 场景包是否与真源同步
```
