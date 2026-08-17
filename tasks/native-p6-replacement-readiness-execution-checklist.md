# NATIVE-P6-002～P6-005 原生替换准备执行清单

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to execute this checklist task-by-task with review checkpoints.

**Goal:** 在不宣布 PWA/Capacitor 退出的前提下，完成原生替换 Runbook、双设备回归矩阵、数据迁移指南和最终退出决策材料。

**Architecture:** 以源码、P1–P7 契约、LEG 覆盖登记和 A3 冻结边界为权威。业务能力只能经 Repository / Facade / Coordinator 使用；UI 只消费 UiState / UiEvent。每个垂直切片都要经过 JVM、APK、设备和文档证据门禁。

**Tech Stack:** Kotlin/Compose、Gradle、Room、WorkManager、PowerShell、ADB、Python `py -m pytest`。

**设备范围：** 仅允许两类设备：

1. `emulator-5554`：Pixel_8_Pro AVD，作为稳定自动化设备。
2. 主力真机：由用户提供 ADB serial 后执行。

主力真机命令统一先设置：

```powershell
$MainDeviceSerial = "用户提供的主力真机 serial"
```

旧 Android、小屏机、备用真机、平板等没有条件的设备统一记录为 `not_available`，不得推断为通过。

---

## 0. 执行前总门禁

**必须先读：**

- `F:\xw\reverse-tutor\AGENTS.md`
- `F:\xw\reverse-tutor\tasks\audit-conclusion-and-plan.md`
- `F:\xw\reverse-tutor\tasks\native-architecture-decision-record.md`
- `F:\xw\reverse-tutor\tasks\wave0-a3-freeze-boundary.md`
- `F:\xw\reverse-tutor\tasks\wave0-a4-ownership-table.md`
- `F:\xw\reverse-tutor\tasks\native-p6-001-legacy-audit.md`
- `F:\xw\reverse-tutor\tasks\native-legacy-coverage-registry.md`
- `F:\xw\reverse-tutor\tasks\native-android-ui-acceptance-checklist.md`
- `F:\xw\reverse-tutor\tasks\native-first-launch-import-prompt-runbook.md`

执行：

- [ ] `git status --short --branch`，记录当前 HEAD 和未提交文件。
- [ ] 确认不把 `.aily_tmp/`、临时日志、截图缓存加入提交。
- [ ] 记录当前远端基线：`git log -1 --oneline`、`git rev-parse origin/Android`。
- [ ] 任何冻结层改动先停止，创建 `F:\xw\reverse-tutor\tasks\change-requests\p6-frozen-change-001.md`，按 A3 八段模板申请批准；后续申请按序号递增。
- [ ] 任何 Track B 能力缺口先提交 `tasks/capability-requests/`，不得在页面内直接拼装多个 Repository 调用。
- [ ] 检查冻结层差异：

```powershell
git diff --name-only -- mobile-native/core/model mobile-native/core/protocol mobile-native/core/llm mobile-native/core/data
```

预期：空输出。

---

## Task 1 · P6-002 替换包 Runbook

**目标：** 形成可执行的原生 APK 安装、备份、导入、回滚和故障处置手册。

**文件：**

- Create: `F:\xw\reverse-tutor\tasks\native-p6-002-replacement-runbook.md`
- Reference: `F:\xw\reverse-tutor\tasks\native-first-launch-import-prompt-runbook.md`
- Reference: `F:\xw\reverse-tutor\tasks\native-p6-001-legacy-audit.md`
- Reference: `F:\xw\reverse-tutor\tasks\native-legacy-entry-inventory.md`

执行步骤：

- [ ] 写明构建命令：

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --console=plain --no-daemon
```

- [ ] 写明 APK 路径、applicationId、版本号、构建时间和 Git commit。
- [ ] 写明安装前备份：导出当前会话/全量备份，记录文件名、校验值和保存位置。
- [ ] 写明两设备安装流程：模拟器 serial 固定为 `emulator-5554`；真机使用 `$MainDeviceSerial`。
- [ ] 写明首次启动流程：权限、导入提示、跳过、导入、dry-run、确认、结果页。
- [ ] 写明导入失败回滚：保留原始 JSON，不覆盖原数据，记录错误摘要和恢复动作。
- [ ] 写明升级失败回滚：保留旧 APK、停止继续安装、恢复备份、重新验证会话和消息。
- [ ] 写明密钥规则：API key 不从旧导出迁移，不写入日志、截图、诊断文本或提交内容。
- [ ] 写明签名铁律：`release.jks`、alias `reverse-tutor`、applicationId 均不得修改。
- [ ] 写明“不代表替换就绪”：Runbook 完成不等于 PWA/Capacitor 退出。

验收：

- [ ] 新人只按 Runbook 能完成模拟器安装、导入 dry-run、确认导入和失败回滚演练。
- [ ] Runbook 中所有命令都使用 PowerShell；Python 只写 `py`，不写 `python`。
- [ ] 无真实 Provider 请求；所有自动化生成场景使用 Fake Runtime。

---

## Task 2 · P6-003 双设备回归矩阵

**目标：** 在可用条件下完成“模拟器 + 主力真机”两级证据，不虚构其他设备覆盖。

**文件：**

- Create: `F:\xw\reverse-tutor\tasks\native-p6-003-device-regression-matrix.md`
- Update: `F:\xw\reverse-tutor\tasks\native-legacy-coverage-registry.md`
- Update: `F:\xw\reverse-tutor\tasks\native-legacy-entry-inventory.md`

### 2.1 构建与安装基线

- [ ] 运行：

```powershell
.\gradlew.bat :app:test :app:lint :app:assembleDebug :app:assembleDebugAndroidTest --console=plain --no-daemon
```

- [ ] 运行 Python 回归：

```powershell
py -m pytest -q --ignore=tests/test_project_homepage.py
```

- [ ] 运行：

```powershell
git diff --check
git diff --name-only -- mobile-native/core/model mobile-native/core/protocol mobile-native/core/llm mobile-native/core/data
```

- [ ] 安装 `app-debug.apk` 与 `app-debug-androidTest.apk`，记录 APK SHA-256、设备 serial、Android 版本和时间。

### 2.2 模拟器必测组

设备固定：`emulator-5554`。

- [ ] `Phase2CoreLoopDeviceTest`：3 项全部通过。
- [ ] `Phase4ImportExportDeviceTest`：导入 dry-run、append/overwrite/new-space、导出、wipe。
- [ ] `Phase5ContextHubDeviceTest`：Context Hub、证据跳转、返回 Chat。
- [ ] `Phase5GraphDeviceTest`：Canvas、空/大/错状态、选择、编辑、Archive/Hide 确认。
- [ ] `Phase5MemoryDeviceTest`：Chat Note → Context Hub 持久化。
- [ ] `Phase5SourcesDeviceTest`：FullyLocal、PartiallyLocal、Failed、FutureAssisted、Unsupported。
- [ ] `BackgroundGenerationSessionDeletionDeviceTest`：删除会话不写消息。
- [ ] `BackgroundGenerationStartupRecoveryDeviceTest`：重启恢复与重新调度。
- [ ] `BackgroundGenerationNotificationDeviceTest`：完成/失败通知和安全文案。
- [ ] `FormalDiagnosticsDeviceTest`：ErrorLog、诊断页和剪贴板脱敏。

批量执行格式：

```powershell
adb -s emulator-5554 shell am instrument -w -r `
  -e class "com.reversetutor.preview.Phase2CoreLoopDeviceTest,com.reversetutor.preview.Phase5ContextHubDeviceTest,com.reversetutor.preview.Phase5GraphDeviceTest,com.reversetutor.preview.Phase5MemoryDeviceTest,com.reversetutor.preview.Phase5SourcesDeviceTest" `
  com.reversetutor.preview.test/androidx.test.runner.AndroidJUnitRunner
```

### 2.3 主力真机必测组

仅在用户提供 `$MainDeviceSerial` 且 `adb devices` 显示 `device` 后执行：

```powershell
adb -s $MainDeviceSerial install -r mobile-native/app/build/outputs/apk/debug/app-debug.apk
adb -s $MainDeviceSerial install -r mobile-native/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s $MainDeviceSerial shell am instrument -w -r `
  -e class "com.reversetutor.preview.Phase2CoreLoopDeviceTest,com.reversetutor.preview.Phase5ContextHubDeviceTest,com.reversetutor.preview.Phase5GraphDeviceTest,com.reversetutor.preview.Phase5MemoryDeviceTest,com.reversetutor.preview.Phase5SourcesDeviceTest" `
  com.reversetutor.preview.test/androidx.test.runner.AndroidJUnitRunner
```

- [ ] 主力真机执行核心闭环、Context Hub、Graph、Memory、Sources 五组测试。
- [ ] 手工检查深色模式、大字号、IME、48dp 触摸目标。
- [ ] TalkBack 只在主力真机进行人工听测；记录焦点顺序，不用 Compose 语义树冒充语音证据。
- [ ] 真机未连接时，矩阵标记 `not_run`，不得标记 Pass。

### 2.4 设备矩阵记录规则

矩阵只允许以下状态：`pass`、`fail`、`blocked`、`not_run`、`not_available`。

- [ ] 模拟器和主力真机分别记录，不合并为“设备通过”。
- [ ] 其他设备行统一写 `not_available`，原因写“当前无设备条件”。
- [ ] 每个失败附日志、测试方法、复现步骤和是否影响生产代码的判断。
- [ ] 设备测试失败不得通过删断言、放宽等待或改产品文案消除。

---

## Task 3 · P6-004 数据迁移指南

**目标：** 让旧 PWA/Capacitor 数据可以安全进入原生，不迁移密钥，不破坏原数据。

**文件：**

- Create: `F:\xw\reverse-tutor\tasks\native-p6-004-migration-guide.md`
- Reference: `F:\xw\reverse-tutor\tasks\native-first-launch-import-prompt-runbook.md`
- Reference: `F:\xw\reverse-tutor\tasks\native-legacy-entry-inventory.md`
- Reference: `F:\xw\reverse-tutor\tasks\native-backend-protocol-data-contract-freeze.md`

执行步骤：

- [ ] 列出支持的导出版本和协议 schema，精确引用 `core:protocol` 源码，不手抄 Kotlin 签名。
- [ ] 定义迁移前备份、文件校验、版本识别和无效 JSON 处理。
- [ ] 定义 dry-run 输出：可导入、跳过、冲突、错误、预计写入数量。
- [ ] 定义 append、overwrite、new-space 三种模式的边界和确认文案。
- [ ] 定义幂等规则：重复 source/message/session 不产生不可控重复。
- [ ] 定义 Room 写入失败回滚和 partial import 报告。
- [ ] 明确 API key、secretRef、Authorization、URL 中敏感字段不迁移、不导出、不进日志。
- [ ] 明确迁移后的验证顺序：会话列表 → 消息时间线 → 来源 → 记忆 → 图谱 → 设置。
- [ ] 为每种失败情况写出用户可执行恢复动作，不出现“请重试”而无上下文。
- [ ] 用模拟器和主力真机各做一次小型 fixture 导入；无真机时标 `not_run`。

验收：

- [ ] 至少准备 valid、invalid、duplicate、secret-containing、large-but-valid 五类 fixture。
- [ ] JVM 测试覆盖 validator、dry-run、append/overwrite/new-space、幂等和脱敏。
- [ ] 设备测试覆盖导入提示、确认、结果页、失败恢复和 wipe 邻接行为。

---

## Task 4 · P6-005 PWA/Capacitor 退出决策材料

**目标：** 只输出证据化决策，不提前宣布旧路径退出。

**文件：**

- Create: `F:\xw\reverse-tutor\tasks\native-p6-005-pwa-exit-decision.md`
- Reference: `F:\xw\reverse-tutor\tasks\native-p6-001-legacy-audit.md`
- Reference: `F:\xw\reverse-tutor\tasks\native-p6-003-device-regression-matrix.md`
- Reference: `F:\xw\reverse-tutor\tasks\native-p6-004-migration-guide.md`

决策表必须逐条列出 28 个 P0 LEG：

- [ ] `verified`：有代码、JVM、设备、契约和回归证据。
- [ ] `waived`：写明豁免原因、产品影响、补偿措施和用户批准记录。
- [ ] `blocked`：写明缺口、责任文件、下一步和预计验证设备。
- [ ] 不得用“代码已实现”替代设备证据。
- [ ] 不得用模拟器通过替代主力真机未执行。

只有以下条件全部满足，才可将结论提交给用户审批：

- [ ] P0 全部 `verified` 或有明确 `waived`。
- [ ] 模拟器矩阵完成。
- [ ] 主力真机矩阵完成，或明确记录 `not_run` 并由用户批准风险。
- [ ] 迁移指南经过至少一轮 fixture 导入演练。
- [ ] 回滚和备份流程可执行。
- [ ] 签名、applicationId、PWA 生产路径无未授权改动。

决策文档结论只能使用：`proposed`、`approved`、`rejected`、`blocked`。未获用户明确批准前必须保持 `proposed` 或 `blocked`。

---

## Task 5 · 收尾与交接

- [ ] 删除本轮临时日志、截图缓存和构建脚本；不删除用户已有 `.aily_tmp/`。
- [ ] 运行 `git diff --check`。
- [ ] 运行冻结层差异检查，必须为空。
- [ ] 只 stage 本清单声明的文件和实际证据，不 stage `.aily_tmp/`。
- [ ] 每个任务单独提交，提交信息包含任务 ID，例如：`docs(p6): add replacement runbook`。
- [ ] 未收到用户明确 push 指令前，只提交本地，不推送远端。
- [ ] 交接报告必须包含：提交号、设备 serial、测试总数、通过/失败/未运行数量、冻结层结果、剩余 P0、下一步。

## 停止条件

遇到以下任一情况立即停止当前任务并报告：

- 需要修改冻结层、Room schema、DAO、SecretStore、协议 DTO 或 `core:llm`。
- 需要真实 Provider、URL、API Key 或外部网络才能证明结论。
- 需要操作未授权目录、物理设备或非 `emulator-5554` 的设备。
- 测试失败原因不清楚，不能用放宽断言或删除测试处理。
- 发现 Track A/B 同时修改同一物理文件。
