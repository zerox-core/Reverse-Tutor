# NEWMP-V1-006 聊天体验阶段完整执行清单

> **给执行 agent 的统一指令**：严格按 Task 0→8 顺序执行；每个 Task 完成后保留代码、测试输出和报告，再进入下一项。发现失败先定位并修复，不能删断言、绕过校验或用假数据制造通过。全部完成后交给主控统一验收；未经主控明确要求，不提交、不推送、不合并、不清理用户数据。

**执行交付格式（每个 Task 必须产出）**：

- 修改文件清单（生产代码/测试代码分开）
- 实际执行的命令与原始结果（通过、失败或环境阻塞必须区分）
- 与验收标准逐条对应的证据
- 未完成项、根因和下一项最小判别实验
- `git diff --check`、冻结路径和敏感信息检查结果

**本阶段不改变产品定位**：用户只看到自然聊天；AI 以学生语气回应；教学算法、检索结构、检查计划、学习台账和原始 JSON 全部留在后台。输出节奏必须像正常聊天，回复要分段并留下互动空间，不能一次性把整道题讲完。

> **执行方式**：按 Task 0→8 顺序执行。每个 Task 完成后保留代码、测试结果和简短报告，交由主控统一验收。除非主控另行要求，不要自行 push、合并或清理已有数据。

**目标**：让聊天保持自然的学生式互动，同时补齐流式回复、后台状态、本地资料入口和多模态图片输入；教学算法、检索细节和学习台账继续隐藏。

**总原则**：Worker 仍是唯一的最终 assistant 写入者；临时流式内容不得写入消息表；资料只通过现有 SourceRepository；不得记录 API key、URL、Authorization、原始 Provider 响应、文件正文或本地 URI。

**设备**：

- 主真机：Huawei BRA-AL00，Android 12，`9CN0223C27017326`
- 虚拟机：`emulator-5554`，仅作 App 行为验证，不作为 Room migration 证据

---

## Task 0：执行前盘点与边界确认

**目的**：确认基线、在途改动和设备状态，避免覆盖并行工作。

**检查文件**：

- `tasks/NEWMP-V1-006-CHAT-EXPERIENCE-PLAN.md`
- `tasks/NEWMP-V1-006-MAIN-PARITY-MATRIX.md`
- `F:/CodexHome/skills/reverse-tutor-development-guard/references/known-issues.md`

**步骤**：

- [ ] 记录当前分支、HEAD、工作树状态；确认不覆盖既有未提交文件。
- [ ] 确认冻结路径：`core/model`、`core/protocol`、`core/data/preferences`、SecretStore；未经明确批准不得修改。
- [ ] 确认当前 APK、虚拟机和真机状态；不清理应用数据，不卸载现有 APK。
- [ ] 写 `tasks/NEWMP-V1-006-TASK0-REPORT.md`，列出基线、在途文件、设备和本阶段允许修改范围。

**停止条件**：发现文件与本清单冲突、存在未说明的并行改动、或设备操作会清理用户数据时，停止并报告。

---

## Task 1：隐藏内部结构化内容并固定单回合节奏

**目的**：聊天气泡只显示自然的学生式内容，不显示教学控制字段、JSON 或检索正文。

**允许修改**：

- `mobile-native/core/llm/.../LlmGenerationLifecycle.kt`
- `mobile-native/core/llm/.../LlmAssistantReplyEnvelopeParser.kt`
- `mobile-native/feature/chat/.../ChatUiState.kt`
- `mobile-native/feature/chat/.../ChatScreen.kt`
- 对应测试

**步骤**：

- [ ] 先新增 Red 测试：含 `checkPlan`、`outcome`、`Action`、`Knowledge point`、`correctness`、`mastery`、原始 JSON 的回复，最终可见文本不得出现这些字段。
- [ ] 新增 Red 测试：普通 Markdown、代码块、表格和短资料摘要仍然可见。
- [ ] 新增 Red 测试：错误答案只保留“诊断 + 一个下一步问题”；连续请求提示时提示层级变化；正确答案后只提出一个练习/迁移动作。
- [ ] 实现统一可见文本投影：结构化字段只进入后台对象；解析失败时使用普通学生式兜底文本。
- [ ] 在请求策略中加入单回合限制：一个教学动作、最多三段或四行、最多一个教师问题，不能自行回答该问题。
- [ ] 运行：

```powershell
.\gradlew.bat :core:llm:testDebugUnitTest --tests "*.LlmAssistantReplyEnvelopeTest" --tests "*.GuidedLearningTurnPlanContextTest" --console=plain --no-daemon
.\gradlew.bat :feature:chat:testDebugUnitTest --tests "*.ChatUiStateTest" --console=plain --no-daemon
.\gradlew.bat :app:testDebugUnitTest --console=plain --no-daemon
```

- [ ] 写 `tasks/NEWMP-V1-006-TASK1-REPORT.md`，记录测试数量和可见文本断言。

**验收标准**：任何内部策略字段、资料正文、原始 JSON 和 Provider 诊断都不能进入聊天气泡；单条回复不能形成完整小节式长答案。

---

## Task 2：流式输出与临时片段生命周期

**目的**：聊天中逐段显示回复，最终只保存一条完整 assistant 消息。

**允许修改**：

- `core/llm` 的 generation runtime、transport 和测试
- `core/data` 的 ChatGenerationRepository、BackgroundGenerationRepository、临时状态组件和测试
- `feature/chat` 的 UI 状态和显示组件
- 必要的 `app` wiring

**步骤**：

- [ ] 新增 Fake runtime Red 测试：chunk 顺序为 `A→B→C`，回调顺序必须一致。
- [ ] 新增 Repository Red 测试：收到多个 chunk 时 UI 回调收到多段，但消息表最终只有一条 assistant。
- [ ] 新增 Red 测试：旧 token、失败、取消、重试不能更新或残留临时片段。
- [ ] 实现 `jobId + generationToken` 绑定的进程内 partial store；设置最大长度；不写 Room、不写日志。
- [ ] 让 Provider transport 支持逐行读取 SSE；解析 OpenAI-compatible、Anthropic、Gemini 的文本片段。
- [ ] 让 Worker 将 chunk 交给临时状态，最终仍调用原有唯一 assistant 持久化路径。
- [ ] 聊天页显示“正在输入”临时片段；完成后刷新正式时间线；应用进程被杀后临时片段自动消失。
- [ ] 运行：

```powershell
.\gradlew.bat :core:llm:testDebugUnitTest --tests "*.ProductionLlmGenerationRuntimeTest" --tests "*.LlmGenerationLifecycleTest" --console=plain --no-daemon
.\gradlew.bat :core:data:testDebugUnitTest --tests "*.ChatGenerationRepositoryTest" --tests "*.BackgroundGenerationRepositoryTest" --console=plain --no-daemon
.\gradlew.bat :feature:chat:testDebugUnitTest --console=plain --no-daemon
```

- [ ] 写 `tasks/NEWMP-V1-006-TASK2-REPORT.md`。

**验收标准**：用户能看到逐段内容；最终只有一条完整 assistant；退出、重启、失败、重试不会产生半截消息或重复消息。

---

## Task 3：聊天内手机资料入口

**目的**：用户不离开聊天即可从手机选择资料，并绑定当前会话。

**允许修改**：

- `mobile-native/app/.../shell/AppShell.kt`
- `mobile-native/feature/chat/.../ChatScreen.kt`
- `mobile-native/feature/chat/.../ReverseTeachingChatScreen.kt`
- 现有 SourceImport/SessionSettings 适配层及对应测试

**步骤**：

- [ ] 新增 UI/契约 Red 测试：附件菜单出现“从手机选择资料”。
- [ ] 新增导入 Red 测试：成功文件进入现有 SourceRepository，绑定当前 session，刷新 source revision。
- [ ] 新增隔离 Red 测试：已排队的回合不读取导入后的新 revision，只有后续回合可使用。
- [ ] 使用系统 `OpenDocument`，复用 `buildSourceImportInput` 与 `SourceRepository.importSource`；不得在 chat 模块复制解析器。
- [ ] 显示解析中、可用、失败三种安全状态；不得显示本地路径或文件正文。
- [ ] 运行：

```powershell
.\gradlew.bat :feature:chat:testDebugUnitTest --tests "*.ChatAttachment*" --tests "*.SessionSettings*" --console=plain --no-daemon
.\gradlew.bat :app:testDebugUnitTest --console=plain --no-daemon
```

- [ ] 写 `tasks/NEWMP-V1-006-TASK3-REPORT.md`。

**验收标准**：从聊天附件菜单选择手机文件后，资料进入当前会话；失败可重试；聊天正文不出现文件路径或全文。

---

## Task 4：多模态能力配置与图片输入

**目的**：正确识别 Qwen 多模态能力，并安全发送本地图片。

**步骤**：

- [ ] 先新增 capability Red 测试：明确支持的 `qwen3.7-flash` 允许图片；未声明的普通 Qwen 模型保持拒绝。
- [ ] 新增 payload Red 测试：本地 `content://` URI 不能原样出现在 Provider 请求体。
- [ ] 新增 Fake multimodal runtime 测试：支持图片成功；不支持图片返回安全错误；图片过大被拒绝；重启恢复不重复发送。
- [ ] 当前阶段可使用保守的 profile 识别；如要增加可编辑、可持久化 `supportsVision` 字段，必须先单独写迁移设计和能力申请，不得直接改表。
- [ ] 通过执行期 resolver 将本地图片转换为有界 base64；OpenAI-compatible、Anthropic、Gemini 使用各自协议字段。
- [ ] 在发送前显示图片缩略图和待发送状态；不支持时显示“当前模型不支持图片输入”。
- [ ] 运行：

```powershell
.\gradlew.bat :core:llm:testDebugUnitTest --tests "*.LlmProfilePolicyTest" --tests "*.ProductionLlmGenerationRuntimeTest" --console=plain --no-daemon
.\gradlew.bat :core:data:testDebugUnitTest --tests "*.ChatGenerationRepositoryTest" --console=plain --no-daemon
```

- [ ] 写 `tasks/NEWMP-V1-006-TASK4-REPORT.md`。

**验收标准**：Qwen 3.7 Flash 可进入图片发送流程；本地 URI 不泄露；超大或不支持图片时安全失败。

---

## Task 5：退出聊天后的状态热更新

**目的**：用户回到首页或重新进入会话时，仍能看到同一个后台任务的真实状态。

**允许修改**：

- `feature/chat` 会话列表模型/状态映射
- `feature/chat` `ChatScreen.kt` 与 `SessionsScreen.kt`
- `app` 现有后台任务查询和刷新 wiring

**步骤**：

- [ ] 新增 Red 测试：Queued/Running 显示“生成中”；Completed 显示最新消息；Failed 显示安全失败状态。
- [ ] 新增 Red 测试：离开聊天后重进，恢复原 `jobId/token`，不创建第二个任务。
- [ ] 新增 Red 测试：同一会话多个历史任务只显示最新仍活动任务。
- [ ] 使用已有 `findActiveJobForSession` 和持久化状态；不得用聊天文本猜状态。
- [ ] 首页和聊天页使用同一安全状态映射；完成后刷新时间线和会话摘要。
- [ ] 运行：

```powershell
.\gradlew.bat :core:data:testDebugUnitTest --tests "*.BackgroundGenerationRepositoryTest" --console=plain --no-daemon
.\gradlew.bat :feature:chat:testDebugUnitTest --tests "*.ChatUiStateTest" --tests "*.Sessions*" --console=plain --no-daemon
.\gradlew.bat :app:testDebugUnitTest --console=plain --no-daemon
```

- [ ] 写 `tasks/NEWMP-V1-006-TASK5-REPORT.md`。

**验收标准**：返回首页能看到当前会话正在生成；重进会话继续观察同一任务；完成后只出现一条正式回复。

---

## Task 6：后台通知

**目的**：应用进入后台时，用通用通知告知生成完成或失败。

**步骤**：

- [ ] 新增通知策略 Red 测试：Running/Completed/Failed 的标题和正文均不含原问题、资料正文、URL、Authorization、key 或异常类名。
- [ ] 新增幂等 Red 测试：同一 job 使用稳定 notification id；重复 Worker 不产生重复通知。
- [ ] 新增权限 Red 测试：通知权限拒绝时，生成和聊天状态不受影响。
- [ ] 仅在应用后台且任务仍运行时发送“正在生成”或通用完成提示；点击通知回到对应 session。
- [ ] 完成/失败后更新并收敛通知；取消和丢弃不发送误导性完成通知。
- [ ] 运行：

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*.BackgroundGenerationNotification*" --tests "*.BackgroundGenerationOutcomeHandler*" --console=plain --no-daemon
```

- [ ] 写 `tasks/NEWMP-V1-006-TASK6-REPORT.md`。

**验收标准**：后台任务完成可收到安全通知；点通知进入正确会话；拒绝通知权限不影响业务。

---

## Task 7：设备人工流程验收

**目的**：验证真实聊天体验与生命周期，不用 Fake 文案代替真实行为。

**步骤**：

- [ ] 构建并覆盖安装 Debug APK；保留应用数据和 APK，不卸载。
- [ ] 虚拟机先验证：短文本发送、流式片段、返回首页、重新进入会话、图片选择入口。
- [ ] 真机执行同样流程：发送一条短消息，观察 Pending→流式→完成。
- [ ] 生成中返回首页，确认会话卡片显示生成中；回到会话确认状态继续更新。
- [ ] 生成中切后台，确认应用不崩溃；回前台确认状态和消息一致。
- [ ] 强制结束应用后重新打开，确认不出现半截 assistant；后台任务若仍可恢复则显示最终消息，否则显示安全失败/可重试状态。
- [ ] 从聊天附件菜单选择手机资料，确认资料绑定当前会话。
- [ ] 选择图片发送，确认支持的 Qwen profile 能进入生成流程；不支持时显示安全提示。
- [ ] 每次 instrumentation 运行后按规范卸载测试包、重新启用宿主并启动 `MainActivity`；本人工流程不卸载宿主。
- [ ] 写 `tasks/NEWMP-V1-006-TASK7-DEVICE-REPORT.md`，记录设备、API、步骤、结果和截图结论；不得保存聊天原文、文件正文或 Provider 原始响应。

**验收标准**：华为 Android 12 真机完成发送、流式、返回首页、切后台、恢复、资料导入和图片入口验证；所有失败都要区分业务失败与环境阻塞。

---

## Task 8：统一验收与交付

**步骤**：

- [ ] 运行全量 JVM：

```powershell
.\gradlew.bat test --console=plain --no-daemon
```

- [ ] 运行 App 检查：

```powershell
.\gradlew.bat :app:lint :app:assembleDebug --console=plain --no-daemon
```

- [ ] 运行 Python 回归：

```powershell
py -m pytest -q --ignore=tests/test_project_homepage.py
```

- [ ] 执行 `git diff --check`。
- [ ] 执行冻结路径检查，确认没有未经批准的 `core/model`、`core/protocol`、`core/data/preferences`、SecretStore 改动。
- [ ] 搜索新增代码和报告，确认无真实 key、URL、Authorization、原始 Provider 响应、文件正文或私人聊天内容。
- [ ] 汇总每个 Task 的测试数量、设备证据、未完成项和环境阻塞。
- [ ] 写 `tasks/NEWMP-V1-006-FINAL-ACCEPTANCE-REPORT.md`，交主控统一审查。

**统一通过条件**：

- 所有受影响 JVM、App、lint、assemble 和 Python 检查通过，或明确标注环境阻塞；
- 真机人工流程有实际证据；
- 流式最终只落一条 assistant；
- 聊天不泄露内部教学结构；
- 本地资料和图片输入安全可用；
- 首页、聊天页、通知三处状态一致；
- 冻结范围和敏感信息检查通过。

---

## 执行禁止事项

- 不删除或弱化断言来制造通过。
- 不把流式片段直接写成多条 assistant 消息。
- 不新增第二个 Worker 或第二个 assistant 写入路径。
- 不把教学算法面板、检索全文、原始 JSON 暴露给用户。
- 不把模型自评直接写成学习事实。
- 不把 `content://` URI 当作远端图片 URL 发送。
- 不为了通过 API 36 instrumentation 修改数据库迁移或 target SDK。
- 不读取、打印、提交或推送 `local.properties` 中的凭证。
