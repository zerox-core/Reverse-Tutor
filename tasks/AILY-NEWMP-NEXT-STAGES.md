# Aily 交接清单：NEWMP 后续多阶段执行

> 主线依据：[PROJECT_DEVELOPMENT_MAINLINE.md](../docs/PROJECT_DEVELOPMENT_MAINLINE.md)
>
> 执行模式：严格串行。每个任务通过 Gate 后才能开始下一项；任一 Gate 未通过立即停止并交回 Codex 审计。
>
> 分支：`newmp`。当前工作树包含 Codex/Aily 在途改动，禁止 reset、checkout、clean、rebase、commit、push、tag，禁止用全量 `git add .` 收拢改动。

## 全局硬规则

- 每次开始先阅读 `AGENTS.md`、`docs/PROJECT_DEVELOPMENT_MAINLINE.md`、`F:\CodexHome\skills\fixed-io-encoding\SKILL.md`、`F:\CodexHome\skills\reverse-tutor-development-guard\SKILL.md` 和其 `references/known-issues.md`。
- PowerShell 每条命令先设置 UTF-8；Android 使用 `ANDROID_HOME=E:\Android\Sdk`、`ANDROID_SDK_ROOT=E:\Android\Sdk`、`GRADLE_USER_HOME=E:\Android\Gradle\newmp`，Python 只使用 `py`。
- 所有测试用 Fake/Static runtime；不得读取或调用真实 Provider、API key、SecretStore。
- `BackgroundGenerationWorker → BackgroundGenerationRepository` 是唯一 Provider 调用与 assistant 消息写入路径；不得重发用户消息、不得创建合成 assistant 消息、不得增加第二写入者。
- UI/feature 只使用已发布 Port/contract；不得访问 DAO、Entity、Database、SQL、Worker、Provider 或 SecretStore。
- 未有明确 P3/P6 批准时，不改 `core:model`、`core:protocol`、`core:llm`、`core:data/*Repository`、`core:data/local`、Room schema/DAO/migration 或 SecretStore。已有 P3/P6 批准只覆盖“富回复/受限工具”和“窗口拓扑/heartbeat”中已经命名的范围；范围不清立即停机。
- 每次 Android instrumentation 后卸载测试包，恢复 `com.reversetutor.preview/.MainActivity` 到前台；不得连续无原因重试同一个设备失败。

---

## Task 0 · Gate：v10 → v11 真实迁移设备验收

**所属主线节点：** V1 富回复/受限工具的前置验收门。
**上游依赖：** P6 v10→v11 已获批准；JVM 迁移/仓库回归已通过。
**允许修改：** 无业务代码。只允许在首次真实迁移失败且确认测试基建缺陷时，修复 AndroidTest assets 或测试代码；先报告根因。
**禁止修改：** 生产 target/min SDK、Room 迁移语义、schema/entity/DAO、现有 AVD 配置。

### 执行步骤

1. 检查已连接 serial 和 API。只能选择 Android 12/13 或 API 34 以下设备；API 36 的 `core:data` instrumentation APK 会被平台拒绝，见 RT-2026-031，必须记录 `blocked`，不得运行后续安装重试。

2. 在 `F:\xw\reverse-tutor-newmp\mobile-native` 运行：

```powershell
[Console]::OutputEncoding=[System.Text.UTF8Encoding]::new($false)
[Console]::InputEncoding=[System.Text.UTF8Encoding]::new($false)
$OutputEncoding=[System.Text.UTF8Encoding]::new($false)
chcp 65001 | Out-Null
$env:ANDROID_HOME='E:\Android\Sdk'
$env:ANDROID_SDK_ROOT='E:\Android\Sdk'
$env:GRADLE_USER_HOME='E:\Android\Gradle\newmp'
.\gradlew.bat :core:data:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.reversetutor.core.data.ReverseTutorDatabaseMigration10To11Test' --console=plain --no-daemon
```

3. 预期：至少执行 1 个测试，`ReverseTutorDatabaseMigration10To11Test` 通过。零测试、安装被拒绝或 JVM 通过都不能算设备证据。

4. 无论通过或失败，都做设备清理：

```powershell
$adb='E:\Android\Sdk\platform-tools\adb.exe'
$serial=(& $adb devices | Select-String '\sdevice$' | ForEach-Object { ($_.Line -split '\s+')[0] } | Select-Object -First 1)
if ([string]::IsNullOrWhiteSpace($serial)) { throw 'No connected device available for cleanup.' }
& $adb -s $serial uninstall com.reversetutor.core.data.test
& $adb -s $serial uninstall com.reversetutor.preview.test
& $adb -s $serial shell pm enable --user 0 com.reversetutor.preview
& $adb -s $serial shell am start -W -n com.reversetutor.preview/.MainActivity
```

**交付证据：** serial、API、实际执行数量、测试方法、结果、清理结果。
**停止条件：** 无兼容设备或安装前拒绝时标记 `blocked`，不要进入 Task 1。

---

## Task 1 · V1 教学算法与旧 main 行为的可证实映射

**所属主线节点：** V1 普通单会话闭环。
**上游依赖：** Task 0 通过。
**允许修改：** `tasks/native-companion-old-main-parity-matrix.md`、`mobile-native/core/domain/**`、`mobile-native/app/**/wiring/session/**` 及对应测试。
**禁止修改：** `main` 分支代码、PWA/Capacitor、`core:model`、`core:protocol`、`core:llm`、`core:data`、Room、Provider transport。

### Red/审计步骤

1. 只读比较 `main:engine.py` 中的教学决策与当前：
   - 浅答 → `Probe`
   - 明确错误规则 → `Challenge`
   - 无切入点 → `Clue` / `ScaffoldExample`
   - “我懂了” → `ExaminerVerify`，不直接写掌握度
   - 到期复习 → `Recap` / `DelayedRetrieval`
   - 模板角色、目标、策略、语气进入稳定 policy/context

2. 在矩阵为每一项记录：旧函数、Native contract、测试方法、状态（`migrated` / `deliberately_replaced` / `unavailable`）。不得把旧 Python prompt 拼接、原始历史启发式或 post-turn memory review 说成已迁移。

3. 先运行：

```powershell
.\gradlew.bat :core:domain:testDebugUnitTest --tests '*SessionTurnPolicyTest' --tests '*GuidedLearningPlanTest' :app:testDebugUnitTest --tests '*BackgroundTurnPreparationCoordinatorTest' --tests '*PostTurnProjectorTest' --console=plain --no-daemon
```

4. 若缺口可在纯 domain/app wiring 内修复，先写能准确暴露缺口的 Red 测试，再做最小 Green。若策略字段无法通过既有 `LlmSessionPolicyContext` 表达，停止并提交一份新的最小 P3 能力申请；不得把字段藏进自由文本。

### 必须保留的安全规则

- `LocalLearningEvidenceVerifier` 是学习台账的唯一证据门；模型 JSON、用户自称理解、普通聊天文本都只是候选，不能写 mastery。
- 当前没有本地客观核验器时，生产默认拒绝学习事实写入；这是已确认的保守行为，不得用模型自评绕过。
- 非学习模式的 evidence 永远是 `none`。

**交付证据：** 更新后的矩阵、Red/Green 命令结果、修改文件与“哪些行为尚不可用”。
**停止条件：** 需要冻结协议或数据层字段时立即停止，不进入 Task 2。

---

## Task 2 · V1 统一验收与可审计交付候选

**所属主线节点：** V1 完成门。
**上游依赖：** Task 1 通过。
**允许修改：** 测试、任务证据文档、`F:\CodexHome\skills\reverse-tutor-development-guard\references\known-issues.md`。
**禁止修改：** 无关生产代码；不提交、不推送。

### 验收清单

1. 运行完整 Android 验收：

```powershell
.\gradlew.bat test :app:lint :app:assembleDebug --console=plain --no-daemon
```

2. 运行 Python 基线：

```powershell
py -m pytest -q --ignore=tests/test_project_homepage.py
```

3. 边界审计：

```powershell
git diff --check
git diff --name-only -- mobile-native/core/model mobile-native/core/protocol mobile-native/core/data/preferences mobile-native/core/data/llm/SecretStore.kt
git status --short --branch
```

4. 对 `core:llm`、`core:data`、Room/schema/migration 的改动逐文件比对 P3/P6 批准范围。禁止把“有批准的局部变更”说成“冻结层未变化”。

5. 执行一次非真实 Provider 的最小会话设备验收：创建/进入会话、发送、观察用户消息和 Pending 或安全失败状态、冷启动后回到同会话。不要重复 Task 0 的迁移测试，也不要记录用户对话原文。

**交付证据：** 全量测试计数、Lint/assemble、Python 结果、设备 serial/API、冻结范围审计、已知阻塞项。
**停止条件：** 任一失败不明时停止并给出最小判别实验；全部通过后停止等待 Codex 审计，不能自行进入 V2。

---

## Task 3 · V2 记忆演进：先补能力申请，再做最小闭环

**所属主线节点：** V2 记忆拓扑。
**上游依赖：** Task 2 经 Codex 审计确认为 V1 完成。
**允许修改（申请前）：** `tasks/capability-requests/**`、`tasks/native-companion-old-main-parity-matrix.md`、`core:domain/**` 与测试。
**禁止修改（申请前）：** `core:llm`、`core:data`、Room、Worker、Repository、schema/migration。

### 先做的事

1. 审计已存在的 `CompanionMemoryEvolutionPolicy`、`LearningScopeGuard`、`CompanionMemoryRepository`：明确哪些是纯策略/持久化准备，哪些尚未进入真实 Worker 回合。

2. 为“本回合产生的有界 memory observation 候选”起草新的 P3/P6 补充申请，至少写清：
   - companion root 才能读写人格、频率和交互偏好；
   - 学习/task/branch 窗口只能产生局部记忆与可汇入全局的学习事实；
   - 不保存 raw transcript、Provider 文本、URL、secret；
   - 高阈值、独立、稳定观察才可覆盖核心设定；短期观察自动过期；
   - 软学习范围信号只记录类别/计数，不监控原文；
   - 输出候选、验证、版本更新和失败回滚的幂等路径。

3. 在用户单独批准该补充申请前停止。不得让模型直接覆盖人格/模板设置，也不得把普通会话内容写进 companion memory。

**交付证据：** 一份 P3/P6 补充申请、纯策略测试结果、未接线能力清单。
**停止条件：** 未获单独批准，不开始持久化/Worker 实现。

---

## Task 4 · V3 子分支窗口：拓扑验收后才开放入口

**所属主线节点：** V3 子分支会话。
**上游依赖：** Task 3 完成并经 Codex 审计；已批准的窗口拓扑 P6 范围仍有效。
**允许修改：** 现有 topology/branch app wiring、feature contracts、对应测试和最小临时宿主。
**禁止修改：** 新建未批准的 Room 表/DAO/migration；最终视觉重设计；同级合并或跨窗口 memory 访问。

### 验收/实现规则

1. 首先写或运行 Red 测试：fork 时历史截断、父 fork 后消息不可见、兄弟消息不可见、子只合并直接父级、幂等 merge、删除子不影响父 merge/global learning ledger。

2. 确保入口只调用已发布 `WindowBranchPort` / `WindowConversationContract`。没有已存在且可证明安全的删除编排时，UI 必须显示不可用，不能伪装删除成功。

3. 一次兼容设备场景：创建子分支 → 本地消息 → 检查可见历史 → 有真实结构化 delta 才尝试合并 → 删除子分支 → 回父会话。设备结束后做测试包清理与宿主恢复。

**交付证据：** 仓库/JVM/设备结果、窗口身份与历史边界验证、删除隔离证据。
**停止条件：** 任意跨父/兄弟可见性或删除边界不可证明时停止，不进入 heartbeat。

---

## Task 5 · V4 heartbeat 主动对话

**所属主线节点：** V4 heartbeat。
**上游依赖：** Task 4 通过。
**允许修改：** 既有 heartbeat domain policy、app coordinator、Worker 调度测试、feature read/command contract。
**禁止修改：** 空 user message 模拟 heartbeat、第二条 assistant 写入路径、未批准的持久化字段、前端 timer/Provider 调用。

### 验收/实现规则

1. Red 测试覆盖：root 默认开启；child 默认关闭；显式启用只影响自身；cooldown/expiry/前台活跃会话阻止派发；`spaceId` 不得等于或替换 `targetWindowId`。

2. initiative job 必须无 `userMessageId`、无用户文本，且带不可变 `InitiativePlan + LlmWindowContext + LlmTurnPlan` 快照。重启恢复不得重复投递。

3. Worker 仍是唯一写入者；Provider/解析/工具失败只能产生安全失败码，不回滚已完成普通聊天回复。

4. 在兼容设备执行一次 root heartbeat 场景及一次 explicit-child 场景；记录计划是否入队、目标窗口、单条 assistant 写入、重启恢复与清理。

**交付证据：** policy/Worker/设备结果、single-writer 证明、默认与显式子窗口差异。
**停止条件：** 若 P3 附录 A 的现有范围不足以覆盖新 job 语义，先更新申请并等用户批准。

---

## Task 6 · V5 学习事实投影与 V6 前端交接

**所属主线节点：** V5 → V6。
**上游依赖：** Task 5 通过。
**允许修改：** 学习读模型/图谱投影 app wiring 与 feature contracts、前端交接文档、对应测试。
**禁止修改：** 从普通聊天文本、工具回执或模型自评推断图谱/mastery；以 UI 按钮反向决定领域语义；提前重做视觉层。

### 验收/交接规则

1. 只让经过 `LocalLearningEvidenceVerifier` 的学习事实进入 overview/graph read model。EMPTY、普通 assistant 文本、资料引用和工具回执必须为 no-op。

2. 发布给前端的稳定输入只有：富内容块、稳定资料 ID、安全工具结果、会话文档/表格快照、分支命令和状态、heartbeat 状态/命令、initiative 状态、学习读模型。不得发布 Room/DAO/Worker/Provider/raw memory。

3. 编写前端交接文档，说明每个状态的成功、空、等待、失败语义与禁止的乐观 UI；不指定最终视觉风格。

4. 运行全量 Android/Python/设备验收，并由 Codex 独立审计后再决定版本化提交与推送。

**交付证据：** 投影 provenance 测试、前端契约文档、全量验收报告。
**停止条件：** 学习事实来源不可追溯或 UI 需要扩展领域字段时，先停机更新契约/能力申请。

---

## Aily 最终回报模板

每个已执行任务单独报告，禁止把未执行项写成通过：

```text
任务编号：
Gate 状态：passed / failed / blocked / not_run
实际修改文件：
Red 测试与实际结果：
Green 实现与实际结果：
定向/模块/全量测试：
设备证据（serial、API、场景、结果、清理）：
冻结范围审计：
工作树状态：
未解决问题：
下一项最小判别实验：
明确声明：未 commit、未 push、未 tag。
```
