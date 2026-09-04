# NEWMP 基建问题修复清单

> 适用分支：`newmp`。本清单只处理测试、设备、构建和生命周期基建问题，不改变会话算法、记忆语义或 Provider 行为。
>
> 当前基线：Android JVM 全量、Lint、Debug APK 和 Python 回归均已通过；V1 业务验收仍被 RT-2026-034 的重启测试基建阻塞。

## 总览

| 任务 | 对应问题 | 类型 | 优先级 | 是否阻塞真实业务开发 | 当前状态 |
|---|---|---|---|---|---|
| I-034 | RT-2026-034 | Compose 设备测试生命周期 | P0 | 否，已通过真实运行时恢复验收 | 已解决 |
| I-007 | RT-2026-007 | 华为设备测试后宿主恢复 | P1 | 否，有固定收尾动作 | 待固化流程 |
| I-015 | RT-2026-015 | 旧迁移测试环境兼容 | P2 | 否，兼容设备迁移已通过 | 待关闭或保留限制 |
| I-031 | RT-2026-031 | Android 36 测试包兼容限制 | P2 | 否，API 31 真机可验收 | 作为已知限制 |

完成定义：I-034 必须通过后，V1 才能标记完成；I-007 必须纳入每次设备测试收尾；I-015/I-031 不得用生产代码改动掩盖，按“兼容设备证据”或“明确保留限制”收口。

---

## I-034 · 修复 V1 核心闭环设备测试生命周期

**目标**：在模拟器上真实验证“创建会话 → 发送 → 安全失败或 Fake 回复 → Activity 重启 → 重新打开同一会话仍保留”，且测试不会丢失 Compose 层级。

**已确认事实**：

- `ChatEntryDiagnosticDeviceTest` 已在 `emulator-5554` 通过 2/2，证明无模型和本地 Fake 配置都能进入聊天。
- `Phase2CoreLoopDeviceTest` 的入口判断已改为聊天输入区和“发送”能力，不再依赖“返回会话首页”标签。
- 当前剩余失败是 `ActivityScenario.recreate()` 后出现 `No compose hierarchies found`，属于测试生命周期绑定问题。

**允许修改**：

- `mobile-native/app/src/androidTest/java/com/reversetutor/preview/Phase2CoreLoopDeviceTest.kt`
- 必要时新增同目录下仅用于重启恢复的设备测试
- `tasks/` 证据文档和 `known-issues.md` 对应条目

**禁止修改**：

- 会话生产代码、Repository、Worker、Provider、数据库结构、业务文案
- 通过增加等待时间、删除重启断言、伪造 assistant 回复来让测试通过

### 执行步骤

- [x] **I-034.1：拆出重启恢复测试**

  将“发送/生成”和“重启后恢复”拆成两个独立设备测试。重启测试使用 `ActivityScenarioRule<MainActivity>` 持有唯一 Activity；测试中不调用当前 `createAndroidComposeRule` 的 `scenario.recreate()` 作为重新绑定方式。

- [x] **I-034.2：先写 Red 测试**

  新测试必须先验证：在同一会话写入一条 Fake 成功消息，结束 Activity，再由新的 ActivityScenario 打开会话，消息仍可见。失败原因必须是生命周期绑定或恢复状态缺口，而不是找不到旧按钮标签。

- [x] **I-034.3：只跑模拟器定向测试**

  工作目录：`F:\xw\reverse-tutor-newmp\mobile-native`。

  ```powershell
  $env:ANDROID_HOME='E:\Android\Sdk'
  $env:ANDROID_SDK_ROOT='E:\Android\Sdk'
  $env:GRADLE_USER_HOME='E:\Android\Gradle\newmp'
  .\gradlew.bat :app:connectedDebugAndroidTest `
    '-Pandroid.testInstrumentationRunnerArguments.class=com.reversetutor.preview.Phase2CoreLoopRestartDeviceTest' `
    '-Pandroid.testInstrumentationRunnerArguments.deviceIds=emulator-5554' `
    --console=plain --no-daemon
  ```

  预期：至少 1 个测试实际执行，重启后同一会话的持久化消息可见；不能接受 0 tests、JVM 结果或只通过入口诊断作为替代证据。

- [ ] **I-034.4：确认后只重跑一次受影响用例**（不再阻塞业务开发；在下次全量设备回归时执行）

  只有 I-034.3 明确通过后，才运行：

  ```powershell
  .\gradlew.bat :app:connectedDebugAndroidTest `
    '-Pandroid.testInstrumentationRunnerArguments.class=com.reversetutor.preview.Phase2CoreLoopDeviceTest#a_coreLoopPersistsNoModelAndMockReplyAfterRestart' `
    '-Pandroid.testInstrumentationRunnerArguments.deviceIds=emulator-5554' `
    --console=plain --no-daemon
  ```

- [x] **I-034.5：设备清理与证据记录**

  无论通过或失败，都执行：

  ```powershell
  $adb='E:\Android\Sdk\platform-tools\adb.exe'
  & $adb -s emulator-5554 uninstall com.reversetutor.preview.test
  & $adb -s emulator-5554 shell pm enable --user 0 com.reversetutor.preview
  & $adb -s emulator-5554 shell am start -W -n com.reversetutor.preview/.MainActivity
  ```

  记录设备 serial、API、实际测试数、失败方法、宿主恢复结果。通过后将 RT-2026-034 更新为 resolved；未通过则保留 blocked，不得进入 V2。

---

## I-007 · 固化华为设备测试收尾流程

**目标**：每次 instrumentation 后，华为主力机都能继续手动打开宿主 App。

**执行步骤**：

- [ ] 每次设备测试前确认设备 serial 为 `9CN0223C27017326`，并执行 `adb -s 9CN0223C27017326 shell svc power stayon true`。
- [ ] 测试结束后卸载 `com.reversetutor.preview.test`；不重复安装、不删除宿主数据。
- [ ] 执行 `adb -s 9CN0223C27017326 shell pm enable --user 0 com.reversetutor.preview`。
- [ ] 执行 `adb -s 9CN0223C27017326 shell am start -W -n com.reversetutor.preview/.MainActivity`。
- [ ] 用 `dumpsys activity activities` 确认 `mResumedActivity` 属于 `com.reversetutor.preview/.MainActivity`。

**完成标准**：连续一次设备测试收尾后，宿主包存在、状态为 enabled、主 Activity 在前台。这个问题不需要阻塞业务实现，但所有设备验收报告都必须附收尾证据。

---

## I-015 · 收口旧迁移测试环境问题

**目标**：明确旧迁移测试是“已由兼容设备覆盖”还是“仍需独立迁移测试”。

**执行步骤**：

- [ ] 在 Android 12/13 或 API 34 以下设备运行旧的 `BackgroundGenerationPolicyMigrationTest`，确认至少一个测试真实执行并通过。
- [ ] 若兼容设备通过：在 RT-2026-015 中补 serial、API、实际测试数，并将状态改为 resolved。
- [ ] 若没有兼容设备：保留 open，但标注为环境限制，不得把 API 36 的零测试结果写成通过。

**禁止修复**：为了迁移测试通过而修改生产 target/min SDK、Room migration 逻辑或删除迁移断言。

**完成标准**：有兼容设备真实证据，或在问题登记中明确保留限制和下一次判别实验。该问题不阻塞当前 V1，因为 v10→v11 已在 Android 12 真机通过。

---

## I-031 · 明确 Android 36 测试包兼容策略

**目标**：防止团队把 Android 36 的安装拒绝误判为业务或迁移失败。

**执行步骤**：

- [ ] 所有 `core:data` instrumentation 优先选择 Android 12/13 或 API 34 以下设备。
- [ ] API 36 只允许运行 app instrumentation；不得用它证明 `core:data` 迁移测试通过。
- [ ] 若未来必须支持 API 36，另立能力/构建申请，单独评估测试 APK target SDK 调整，并补完整迁移回归；本清单不直接改生产 SDK 配置。

**完成标准**：在开发文档和设备验收命令中明确设备选择规则；API 36 的 `INSTALL_FAILED_DEPRECATED_SDK_VERSION` 作为已知环境限制记录，不再重复尝试。

---

## 统一验收门

- [ ] I-034 通过：V1 设备闭环真实执行，重启恢复有证据。
- [ ] I-007 已执行：主力机宿主恢复并在前台。
- [ ] I-015 已关闭或明确保留兼容限制。
- [ ] I-031 已写入设备选择规则，不再把 API 36 拒绝当作产品失败。
- [ ] `git diff --check` 通过。
- [ ] 受保护路径无未批准修改。
- [ ] `:core:domain:testDebugUnitTest :feature:chat:testDebugUnitTest :app:testDebugUnitTest` 通过。
- [ ] `test :app:lint :app:assembleDebug` 通过。
- [ ] Python 回归保持 `510 passed, 28 skipped` 或更好。

统一门通过后，才能把 `NEWMP-V1-001` 标记为完成并进入 V2 真实记忆业务场景；RT-007/015/031 即使保留为已知限制，也不能伪装成未发生。
