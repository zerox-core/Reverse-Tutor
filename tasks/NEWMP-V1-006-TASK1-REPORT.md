# NEWMP-V1-006 Task 1 报告：隐藏内部结构化内容与单回合节奏

日期：2026-09-06 · 执行通道：Cloudflare 本地开发 MCP（java_development / read_file / write_file / git_workflow / git_diff）· 项目：`F:\xw\reverse-tutor-newmp`（分支 `newmp`）

## 1. 目标与 Red-Green 过程

Task 1 覆盖两条验收线：

1. **内部结构化内容不出现在聊天气泡**：教学算法（Outcome/Correctness/Mastery/Depth）、检查计划（checkPlan/Evidence）、结构化 JSON（envelope 原文）在任何进入可见时间线的路径上都必须被过滤或替换为学生式兜底文案。
2. **单回合节奏（回复自然、分段、保留互动空间）**：prompt 契约锁定一回合一个教学动作、最多三段/四行、最多一个问题。

### Red 阶段（先写测试，确认失败）

在 `LlmAssistantReplyEnvelopeTest.kt` 新增 4 个测试（保留原 11 个）：

- `visibleTimelineTextHidesOutcomeCheckPlanCorrectnessAndMasteryLabels`：段落中夹带 Outcome:/Correctness:/Mastery:/Depth:/Evidence type:/Evidence status:/Process summary:/checkPlan:/Initiative source:/Window id: 控制行必须被过滤，前后学生文案保留。
- `envelopeSidebandsNeverLeakIntoTimelineText`：outcome/checkPlan 对象字段值不进可见文本。
- `rawEnvelopeShapedJsonFallsBackToStudentTextInsteadOfLeaking`：envelope 形状但无法严格解析的 raw JSON，经 `toVisibleTimelineText()` 与 parser fallback 两条路径都必须返回「我还没整理好这一步，能再给我一点提示吗？」，且不含 `{`/`blocks`。
- `normalMarkdownCodeTablesAndBulletsRemainVisible`：Heading/Paragraph/CodeBlock/SimpleTable/BulletList 合法富内容全部保留。

Red 运行（`gradle_test`，workdir=`mobile-native`）：`57 tests completed, 2 failed`，`:core:llm:testDebugUnitTest` BUILD FAILED（19s）：
- `visibleTimelineTextHidesOutcomeCheckPlanCorrectnessAndMasteryLabels` → AssertionError（控制行正则未覆盖新标签，内部行残留在可见文本）
- `rawEnvelopeShapedJsonFallsBackToStudentTextInsteadOfLeaking` → ComparisonFailure：期望兜底文案，实际泄漏 `{"version":"v1",...}` 原文

另 2 个新测试（sidebands、markdown 保留）在 Red 阶段即通过——分别证明现有 timelineText 不泄漏结构化 sideband 字段、合法富内容路径未受损。

### Green 阶段（最小修复，单文件）

`LlmGenerationLifecycle.kt` 仅改可见文本投影一处：

1. `toVisibleTimelineText()` 入口新增 envelope 形状检测 `looksLikeAssistantReplyEnvelopeJson()`：整段文本 trim 后以 `{` 开头、`}` 结尾且含 `"blocks"/"version"/"outcome"/"checkPlan"` 键提示 → 整体替换为 `VisibleTimelineFallbackText`（提取为 `internal const`，与原 ifBlank 兜底同文案）。整段形状检查刻意收窄：正文里出现花括号不会误伤。
2. `InternalVisibleControlLine` 正则追加 11 个标签：`outcome|correctness|mastery|depth|evidence type|evidence status|process summary|checkplan|initiative source|window id`。
3. 单回合节奏：`reverseTutorStudentPromptBlock()` 已含 one move/three paragraphs/four lines/one question 约束（本阶段前已实现，非本次改动），本次在 `LlmGenerationLifecycleTest.kt` 新增 `reverseTutorStudentPromptBlockLocksSingleTurnRhythmAndInteractionSpace` 锁定测试防止回归。该子项属「已实现行为 + 回归锁定」而非 Red→Green，如实说明。

### Green 运行

`gradle_test`（全模块 debug+release 单元测试）：**BUILD SUCCESSFUL in 1m 18s，exitCode 0，465 actionable tasks（58 executed / 407 up-to-date）**。

定向套件计数（testDebugUnitTest XML）：

- `LlmAssistantReplyEnvelopeTest`：tests=15，failures=0，errors=0，skipped=0
- `LlmGenerationLifecycleTest`：tests=10（含新增节奏锁定测试），failures=0，errors=0，skipped=0

## 2. 修改文件清单

| 文件 | 改动 |
|---|---|
| `mobile-native/core/llm/src/main/java/com/reversetutor/core/llm/LlmGenerationLifecycle.kt` | envelope 形状 JSON 检测 + 兜底常量提取 + 控制行正则追加 11 标签（diff 3,062 字符，仅可见文本投影段） |
| `mobile-native/core/llm/src/test/java/com/reversetutor/core/llm/LlmAssistantReplyEnvelopeTest.kt` | 原 11 测试保留，新增 4 个 Red 测试（15 总数） |
| `mobile-native/core/llm/src/test/java/com/reversetutor/core/llm/LlmGenerationLifecycleTest.kt` | 新增单回合节奏锁定测试（9→10） |

写入均经回读字节级比对一致（15,389 / 33,553 / 12,517 bytes）。

## 3. 实际执行命令与真实结果

1. `aily-mcp call write_file`（3 次）→ `Successfully wrote N bytes`，回读逐一比对一致。
2. `aily-mcp call java_development {action:gradle_test, workdir:F:\xw\reverse-tutor-newmp\mobile-native}`（Red 轮）→ `ok:false, 57 tests completed, 2 failed`，报告 `core/llm/build/reports/tests/testDebugUnitTest/index.html`。
3. 同命令（Green 轮）→ `ok:true, exitCode:0, BUILD SUCCESSFUL in 1m 18s`。
4. `aily-mcp call git_workflow {action:status}` → 分支 `newmp`，3 个 M（上表文件）+ 2 个未跟踪（checklist 与 Task 0 报告，均为前序产物）。
5. `aily-mcp call git_diff`（lifecycle 文件）→ 最小 diff 3,062 字符，无整文件重写（行尾 LF 与原文件一致）。

## 4. 设备型号/API/测试步骤

Task 1 为纯 JVM 单元测试层，无设备操作（设备验证归 Task 7）。宿主机：Windows（hostname 六少）。

## 5. 验收标准逐条结论

| 验收标准 | 结论 |
|---|---|
| 内部教学算法/检查计划/检索正文/JSON 不出现在聊天气泡 | ✅ 控制行过滤 + envelope JSON 整体兜底，测试锁定（2 个 Red→Green 测试） |
| 合法富内容（Markdown/代码/表格/列表）保持可见 | ✅ `normalMarkdownCodeTablesAndBulletsRemainVisible` 通过 |
| 回复自然、分段、保留互动空间（单回合节奏） | ✅ prompt 契约锁定测试通过（约束为前序已实现，本任务补回归锁定） |
| 先 Red 后 Green | ✅ Red：2 失败证据存档；Green：全绿 |
| 不删/弱化断言 | ✅ 原 11+9 个测试全部保留通过 |
| 不新增第二条 assistant 写入路径 | ✅ 只改投影函数，无持久化路径改动 |

## 6. 未完成项、根因与最小判别实验

无阻塞性未完成项。已知边界：

- envelope 形状检测为整段文本形状启发式（`{...}` + envelope 键）。若模型在**单个 CodeBlock 中**返回完整 envelope 形状 JSON 且该块是唯一内容，将被兜底替换——这是「JSON 不进气泡」要求的预期行为，但若未来教学场景需要展示 JSON 代码示例，需改用块类型白名单。最小判别实验：构造仅含一个 JSON CodeBlock 的 envelope，断言当前行为（兜底）是否符合产品预期。
- 通道限制：无法在 Windows 侧跑任意 shell（如 `git diff --check` 原命令），以 git_diff 工具 + 尾随空白扫描等效替代（见下节）。

## 7. git diff --check / 冻结路径 / 敏感信息检查

- **git diff --check 等效**：git_diff 输出无 `trailing whitespace`/`space before tab` 标记；三个改动文件逐行扫描尾随空白 = 0 行。Git stderr 提示 `LF will be replaced by CRLF`（仓库 autocrlf 行为，非本次引入）。
- **冻结路径**：改动仅 `core/llm` runtime 两个测试文件 + `LlmGenerationLifecycle.kt`（允许清单内）；未触碰 `core/model`、`core/protocol`、`core/data/preferences`、SecretStore、Room schema/DAO/migration。
- **敏感信息扫描**：改动内容无真实 key/URL/Authorization/原始 Provider 响应/文件正文/私聊内容。唯一 `Authorization:` 命中位于**原有**测试夹具的假 token `tok-1`（前序已存在，本次未改动该行）。
