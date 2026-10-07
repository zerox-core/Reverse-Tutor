# NEWMP-V1-007 接管审计与阶段任务表

日期：2026-09-09 · 审计人：本地开发搭档（Aily，经 Cloudflare 本地开发 MCP） · 分支：`newmp` · 基线：`e3c4632` + 27 项未提交改动

> 本文件是 NEWMP-V1-006 交接后的接管审计，并给出自 V1-006 收口到 V6 的阶段性任务表。
> 主线依据：`docs/PROJECT_DEVELOPMENT_MAINLINE.md`；硬规则依据：`AGENTS.md`。
> **测试策略变更（用户 2026-09-09 指定）：后续设备测试以虚拟机（Pixel_8_Pro · API 36 · emulator-5554）为主，真机为可选补充。**

## 一、审计范围与方法

实际读取并核对：`AGENTS.md`、`docs/PROJECT_DEVELOPMENT_MAINLINE.md`、`docs/CODEX_HANDOFF.md`、`tasks/NEWMP-V1-006-CHAT-EXPERIENCE-PLAN.md` / `-FULL-EXECUTION-CHECKLIST.md` / `-FINAL-ACCEPTANCE-REPORT.md` / `-HANDOFF-REPORT.md` / `-TASK7-DEVICE-REPORT.md`、`tasks/AILY-NEWMP-NEXT-STAGES.md`、`tasks/capability-requests/` 与 `tasks/change-requests/` 目录、`mobile-native/settings.gradle.kts`、`git status` 与 `git diff`（全量 unstaged diff 抽样核对）。

## 二、项目全景（审计基线）

### 2.1 三条实现线

| 线 | 位置 | 现状定位 |
|---|---|---|
| Python 后端 | `server.py` / `engine.py` / `db.py` / `kg_*.py` 等 | 参考实现 / 测试基线（510 passed, 28 skipped），冻结不动 |
| PWA / Capacitor | `static/app/`、`mobile/` | 行为参考；退出须等 Phase 6 且由用户批准 |
| native Android | `mobile-native/` | **当前唯一生产主线**（分支 `newmp`） |

### 2.2 native 模块拓扑（11 个 Gradle 模块）

`app` + `core:{protocol, model, design, domain, data, llm, remote}` + `feature:{chat, memory, sources, settings}`。依赖方向铁律：UI/feature → Port/UiState → App wiring → Domain policy → Repository/Runtime → Room/Worker/Provider。UI 不得直接触 DAO/Entity/Database/SQL/Worker/Provider/SecretStore。

### 2.3 治理体系

- 主线宪章：拓扑式开发顺序 V0（基线门禁）→ V1（单会话最小闭环，当前）→ V2（记忆拓扑）→ V3（子分支窗口）→ V4（heartbeat）→ V5（学习事实与图谱投影）→ V6（最终前端与副屏视觉）；每切片九层齐备才算完成，TDD 强制 Red→Green→Refactor→Acceptance。
- 冻结边界（AGENTS.md）：`core/model`、`core/protocol`、`core/llm`、`core/data/*Repository`、`core/data/local`、`core/data/preferences`、`SecretStore`、Room schema/migration——改动需明确批准，走 `tasks/capability-requests/`。
- 提交纪律：默认不 commit / 不 push / 不打 tag，等用户明确授权。

## 三、当前状态核实（审计事实）

1. **工作树**：27 项未提交（14 修改 + 13 新增），与验收报告清单逐一吻合；两个临时截图（`.tmp-chat.png`、`.tmp-emulator.png`）已于本轮移出工作树（MCP 无删除工具，移至 `F:\.aily-trash\`）。
2. **V1-006 六类能力全部有测试证据**（全绿）：气泡泄漏防护 EnvelopeTest 15/15、LifecycleTest 10/10；流式生命周期 BgRepo 19/19 + ChatGenRepo 14/14 + feature:chat 243/243；聊天内资料导入 MapperTest 4/4；多模态 RuntimeTest 13/13；首页卡片状态 SessionCardGenerationTest 3/3；通知策略 10/10。全量 JVM（465 tasks）、`:app:lint`、`:app:assembleDebug` 均 BUILD SUCCESSFUL；Python 回归 510 passed / 28 skipped。
3. **设备证据现状**：API 36 虚拟机已做 UI / 生命周期验证（2026-09-07：覆盖安装、主页/聊天/附件菜单、图片与资料入口、强杀重启恢复），但**未发起真实模型请求**；真机 BRA-AL00（9CN0223C27017326）不在 ADB 列表。
4. **通道现状**：MCP 四条设备通道均受阻（android_development 门禁 / plan_environment_changes INTERNAL_ERROR / staging profile 不适用 / 无通用 shell）。虚拟机操作目前只能由用户或 Codex 在 Windows 本机执行；JVM/Gradle/lint/assemble 我方可经 `java_development` 自动执行。
5. **敏感信息与冻结偏好域**：验收报告扫描 0 真实敏感（8 处命中全为测试假数据）；本轮实测 `git status` 复核 `core/model`、`core/protocol`、`core/data/preferences`、`SecretStore` 零改动，一致。

## 四、审计发现（按处置优先级）

- **F1（提交门必须确认）冻结边界口径差异**：AGENTS.md 冻结清单包含 `core:llm` 与 `core:data/*Repository`，而 V1-006 实际修改了 `core/llm/LlmGenerationLifecycle.kt`（+3 个 core:llm 测试文件）与 `core/data/background/BackgroundGenerationRepository.kt`（+3 行 partialStore 清理）。V1-006 计划文档将这些文件列入任务范围、TASK2 报告声明"允许清单内"，但仓库内**未见对应的冻结层变更批准记录**（capability-requests/ 现有 6 份均为 P3/P6 主题）。按主线宪章 §9"冻结路径检查无未批准改动"是完成门禁之一——**commit 前需主控（用户）对这批改动做追溯确认**。
- **F2（V1-006 唯一实质缺口）真实生成闭环证据缺失**：流式、单条 assistant 落库、后台通知、图片请求均无真实模型请求的设备证据。改为虚拟机主策略后，此项落在阶段 S2 执行。
- **F3（已知差距）通知精确路由**：点击通知回 MainActivity 首页，未携带 sessionId；V1-006 验收报告已定位最小改法（通知 Intent extra + MainActivity/AppShell 分支），留待阶段 S1。
- **F4（环境约束）API 36 instrumentation 限制（RT-2026-031）**：Room migration 类 instrumentation 在 API 36 被平台拒绝，需 API ≤ 34 设备/AVD；无则如实标 blocked，不得用 API 36 冒充。
- **F5（待申请）`supportsVision` 持久化字段未做**：当前按 profile 规则保守识别；独立可编辑持久化字段涉及数据协议与迁移评估，须走能力申请后再动。
- **F6（低风险边界）SSE 跨行续接**：流式解析按单行 `data:` 处理；现网 OpenAI/Anthropic/Gemini 均单行，已在 TASK2 报告登记，不阻塞。
- **F7（纪律确认）提交状态**：未 commit / 未 push / 未 tag，与 AGENTS.md 及交接报告一致；本审计同样遵守。
- **F8（轻微）git 行尾告警**：`LF will be replaced by CRLF` 为仓库 autocrlf 行为，非本次引入，无需处理。

## 五、测试策略（虚拟机为主，自本文件起生效）

1. **主验证设备**：Pixel_8_Pro AVD · API 36 · `emulator-5554`。覆盖：UI/生命周期、真实模型请求闭环（流式/单条落库/后台通知/图片）、强杀重启恢复、资料导入。
2. **例外**：Room migration instrumentation 需 API ≤ 34（建议用户按需建一个 API 34 AVD 备用）；无兼容设备时该类证据标 `blocked`。
3. **执行分工**：JVM 定向/全量、lint、assemble 由我方经 `java_development` 自动执行；虚拟机安装/操作/截图由用户或 Codex 在 Windows 本机执行，我方出清单、回收证据并更新报告。真机 BRA-AL00 仅作可选补充，不阻塞任何阶段。
4. 每阶段设备验收失败须区分**业务问题 / 环境问题**；环境问题标 blocked，不得用 JVM 结果冒充设备证据（宪章 §5.4）。

## 六、阶段性任务表（严格串行，逐阶段 Gate）

| 阶段 | 名称 | 主线节点 | 产出 | 验收门 |
|---|---|---|---|---|
| S0 | 接管审计与提交决策（当前） | — | 本文件；F1 冻结层追溯确认；commit 授权决策 | 工作树处置明确（提交或保持） |
| S1 | 通知精确路由（纯代码） | V1-006 收尾 | 通知 Intent 携带 session 标识 + MainActivity/AppShell 路由 + 测试 | Red→Green；app/feature:chat 回归绿 |
| S2 | 虚拟机真实生成闭环验收 | V1-006 关闭门 | TASK7 清单虚拟机版全量执行 + 报告更新 | 真实模型请求闭环证据（含 S1 通知点击） |
| S3 | V1 完成门：旧 main 行为映射与统一验收 | V1 完成 | parity 矩阵更新 + 统一验收报告 | 全量回归 + 虚拟机最小会话验收；Codex 审计后宣布 V1 完成 |
| S4 | V2 记忆拓扑 | V2 | 能力申请 → 批准 → 最小闭环 | 申请获批前不动持久化/Worker |
| S5 | V3 子分支窗口 | V3 | fork 快照/隔离时间线/父级导航/安全删除合并 | 拓扑边界 Red 测试全绿 |
| S6 | V4 heartbeat 主动对话 | V4 | InitiativePlan + Worker 单写入者 | 单写入者证明 + 默认开关语义 |
| S7 | V5 学习事实投影 + V6 前端交接 | V5→V6 | 投影 provenance + 前端契约文档 | 仅 Verifier 认证事实进图谱 |

各阶段细则（按宪章 §8 交接格式）：

### S0 · 接管与提交决策
上游依赖：无。允许修改：仅本审计文件。禁止：reset/checkout/clean/rebase/全量 `git add .`。内容：F1 冻结层改动（`core/llm` 3 文件 + `core/data/background` 1 文件）请用户做追溯确认；确认后由用户决定是否版本化提交 V1-006 批次（27 项）。停止条件：F1 未确认前不进入 S1 之后的提交动作（S1 开发可先行，提交合并等待确认）。

### S1 · 通知精确路由（TDD，纯代码）
上游依赖：S0。允许修改：通知 Intent 构造、`MainActivity`/`AppShell` 路由分支、对应 app/feature:chat 测试。禁止：新增 assistant 写入路径；通知携带问题正文/资料正文；改动 `core:data` 冻结层。Red：先写失败测试——通知 PendingIntent 携带 job 对应 session 标识，冷启动/热恢复两条路径点击后进入对应会话。回归：`:app:testDebugUnitTest` + `:feature:chat:testDebugUnitTest` + 全量 `test`。设备验收：并入 S2 一次执行。停止条件：路由需要改 domain/protocol 契约时停机提交能力申请。

### S2 · 虚拟机真实生成闭环验收（V1-006 关闭门）
上游依赖：S1。允许修改：无业务代码；仅允许修复设备证据暴露的真实缺陷（先报告根因再改）。执行：在 `emulator-5554`（API 36）覆盖安装当前 Debug APK，按 TASK7 清单 8 项执行真实模型请求闭环（短消息 Pending→流式→单条落库；生成中返回首页卡片"生成中"；切后台/回前台一致；强杀重启无半截消息；资料导入绑定；图片发送（qwen3.7-flash profile）；通知观察与点击（验证 S1 路由）；失败案例安全文案）。每步记录通过/失败现象+截图，失败区分业务/环境。交付：更新 `NEWMP-V1-006-TASK7-DEVICE-REPORT.md` 与 `FINAL-ACCEPTANCE-REPORT.md`。停止条件：虚拟机无法驱动（由用户/Codex 执行）时标 blocked 并交回清单。

### S3 · V1 完成门（对齐 AILY-NEWMP-NEXT-STAGES Task 1/2）
上游依赖：S2。内容：①只读比较 `main:engine.py` 教学决策与 native domain 契约（Probe/Challenge/Clue/ExaminerVerify/Recap 等），更新 `native-companion-old-main-parity-matrix.md`，缺口在纯 domain/app wiring 内先 Red 后 Green，策略字段无法表达时停机提能力申请；②统一验收：全量 JVM + lint + assemble + Python 回归 + 虚拟机最小会话验收（创建/发送/Pending 或安全失败/冷启动回会话）；③Room migration instrumentation 如需设备证据 → API ≤ 34 AVD，无则标 blocked。禁止：改 main 分支、PWA、`core:model`/`core:protocol`/`core:llm`/`core:data`、Room、Provider transport。停止条件：任一失败原因不明即停；全过后停等 Codex 审计，不自行进入 V2。

### S4 · V2 记忆拓扑
上游依赖：S3 经审计确认 V1 完成。先做：审计既有 `CompanionMemoryEvolutionPolicy`/`LearningScopeGuard`/`CompanionMemoryRepository`，起草"有界 memory observation 候选"P3/P6 补充申请（companion root 才能读写人格/频率；不存 raw transcript/Provider 文本/URL/secret；高阈值稳定观察才可覆盖核心设定）。**申请获批前不动持久化/Worker。**

### S5 · V3 子分支窗口
上游依赖：S4。Red 覆盖：fork 历史截断、父 fork 后消息不可见、兄弟不可见、子只合并直接父、幂等 merge、删子不影响父。入口只走已发布 `WindowBranchPort`/`WindowConversationContract`；无安全删除编排时 UI 显示不可用。虚拟机跑一次 fork→本地消息→可见历史→合并→删除→回父场景。

### S6 · V4 heartbeat
上游依赖：S5。Red 覆盖：root 默认开、child 默认关、cooldown/expiry/前台活跃阻止派发、`spaceId` ≠ `targetWindowId`；initiative job 无 userMessageId、无用户文本、不可变快照；Worker 唯一写入者。虚拟机跑 root + explicit-child 各一次。

### S7 · V5 投影 + V6 前端交接
上游依赖：S6。只让经 `LocalLearningEvidenceVerifier` 的学习事实进 overview/graph read model；发布给前端的稳定输入仅限宪章 §Task6 清单；写前端交接文档（各状态成功/空/等待/失败语义与禁止乐观 UI）；全量验收 + Codex 独立审计后再决定版本化提交与推送。

**每阶段统一收口**：全量 JVM + lint + assemble（+ 涉及 Python 时 Python 回归）→ 更新批次报告 → `git diff --check` + 冻结路径 + 敏感信息自查 → 停等用户 commit/push 决策。

## 七、待用户决策项

1. **F1 追溯确认**：V1-006 对 `core/llm` 与 `core/data/background` 的改动（计划范围内、带测试、+3 行级最小修复）是否予以确认，作为提交门依据。
2. **S0 提交决策**：当前 27 项是否版本化提交（如 `NEWMP-V1-006: chat experience & multimodal input`）；或先保持工作树、与 S1/S2 合并提交。
3. **API ≤ 34 AVD**：是否建一台备用（仅 Room migration instrumentation 需要；不建则该类证据按规则标 blocked）。
