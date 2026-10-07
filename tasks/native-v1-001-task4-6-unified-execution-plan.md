# NEWMP-V1-001 Task 4–6 统一执行计划

> 交接对象：Aily；Task 4、5、6 连续执行完后由主控统一验收。
> 所属主线：`newmp` 唯一主线 → V1 普通单会话最小垂直闭环

**目标：** 完成结构化回合结果投影、重启后的会话状态恢复，并形成一次完整的自动化与最小真机验收证据。

**架构约束：** UI 只消费 Feature contract/UiState；生成只能经过既有 Port → Coordinator → Repository/Runtime/Worker；学习台账只接受已验证的 `StructuredTurnOutcome`，不得把聊天文本或 Provider 原文伪造成学习事实。若现有契约无法携带真实结构化结果，停在能力申请，不偷偷扩展字段。

**执行纪律：** 每个 Task 按 Red → Green → Refactor；不得 commit、push、tag，不得重置主控已有改动。设备未连接记 `not_run`。

---

## Task 4：结构化回合结果投影到学习台账

### 文件范围

先读：

- `F:\xw\reverse-tutor-newmp\mobile-native\app\src\main\java\com\reversetutor\preview\wiring\session\PostTurnProjector.kt`
- `F:\xw\reverse-tutor-newmp\mobile-native\core\llm\src\main\java\com\reversetutor\core\llm\LlmGenerationLifecycle.kt`
- `F:\xw\reverse-tutor-newmp\mobile-native\core\data\src\main\java\com\reversetutor\core\data\background\BackgroundGenerationRepository.kt`
- `F:\xw\reverse-tutor-newmp\mobile-native\core\data\src\main\java\com\reversetutor\core\data\learning\LearningLedgerRepository.kt`

允许修改：app wiring projector/adapter 与 app 测试；只有在已批准 P3/P6 契约确实不足时才可修改批准范围内 core:data/core:llm 生产文件。禁止修改 core:model、core:protocol、SecretStore、Provider transport、第二条 assistant 写入路径。

### Step 4.1 — Red 测试

在 `F:\xw\reverse-tutor-newmp\mobile-native\app\src\test\java\com\reversetutor\preview\wiring\session\PostTurnProjectorTest.kt` 增加：

```kotlin
@Test
fun valid_study_outcome_is_recorded_once_and_is_bounded() = runBlocking {
    val received = mutableListOf<StructuredTurnOutcome>()
    val projector = PostTurnProjector(TurnProjectionSink { received += it })
    val outcome = StructuredTurnOutcome(
        windowId = "session-1",
        actionType = "probe",
        studentRole = "probing_student",
        knowledgePoint = "函数单调性",
        correctness = 0.8f,
        depth = 0.7f,
        evidenceType = "explanation",
        evidenceStatus = "passed",
        processSummary = "完成一次可复述解释"
    )

    assertTrue(projector.project("job-1", outcome))
    assertFalse(projector.project("job-1", outcome))
    assertEquals(1, received.size)
    assertTrue(received.single().knowledgePoint.length <= 120)
    assertTrue(received.single().processSummary.length <= 320)
}

@Test
fun empty_or_non_learning_outcome_does_not_write_mastery() = runBlocking {
    var writes = 0
    val projector = PostTurnProjector(TurnProjectionSink { writes++ })

    assertFalse(projector.project("empty", StructuredTurnOutcome.EMPTY))
    assertFalse(projector.project("none", StructuredTurnOutcome(
        windowId = "session-1",
        actionType = "observe",
        studentRole = "companion",
        evidenceType = "none",
        evidenceStatus = "none"
    )))
    assertEquals(0, writes)
}
```

在 `F:\xw\reverse-tutor-newmp\mobile-native\core\data\src\test\java\com\reversetutor\core\data\background\BackgroundGenerationRepositoryTest.kt` 增加完成 job 的 round-trip 断言：带真实 envelope/TurnPlan 的 job 能恢复结构化 outcome；无 envelope 的普通旧 job 仍返回 `StructuredTurnOutcome.EMPTY`。不得把 assistant 文本当作 outcome。

### Step 4.2 — Red 命令

工作目录 `F:\xw\reverse-tutor-newmp\mobile-native`：

```powershell
$env:ANDROID_HOME='E:\Android\Sdk'
$env:ANDROID_SDK_ROOT='E:\Android\Sdk'
$env:GRADLE_USER_HOME='E:\Android\Gradle\newmp'
.\gradlew.bat :app:testDebugUnitTest --tests "*.PostTurnProjectorTest" --console=plain --no-daemon
.\gradlew.bat :core:data:testDebugUnitTest --tests "*.BackgroundGenerationRepositoryTest" --console=plain --no-daemon
```

若现有代码已满足，写明“测试钉住已有行为，0 个生产文件修改”。若发现缺少真实 outcome 来源，停止并提交能力申请。

### Step 4.3 — Green 规则

- 只接受 `StructuredTurnOutcome.normalized()` 后的字段并按 jobId 幂等。
- 只有 `windowId`、非空 knowledgePoint、evidenceType/status 均非 `none` 才能映射为 `LearningFactReceipt`。
- companion/goal/非学习结果、EMPTY 均不得写 mastery。
- 不保存 raw user/assistant/Provider 文本、URL、Authorization 或 key。
- 没有真实结构化 outcome 时保留 EMPTY，不从 processSummary 或聊天文本猜掌握度。

### Step 4.4 — Green 回归

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*.PostTurnProjectorTest" --console=plain --no-daemon
.\gradlew.bat :core:data:testDebugUnitTest --tests "*.BackgroundGenerationRepositoryTest" --console=plain --no-daemon
```

预期：`BUILD SUCCESSFUL`；若需冻结生产代码变更，立即停止。

---

## Task 5：重启恢复与安全 UI 状态投影

### 文件范围

- `F:\xw\reverse-tutor-newmp\mobile-native\feature\chat\src\main\java\com\reversetutor\feature\chat\ChatUiState.kt`
- `F:\xw\reverse-tutor-newmp\mobile-native\feature\chat\src\main\java\com\reversetutor\feature\chat\ChatScreen.kt`
- `F:\xw\reverse-tutor-newmp\mobile-native\feature\chat\src\main\java\com\reversetutor\feature\chat\SessionAssistantHostBridge.kt`
- 对应 feature/chat 测试与 app 导航测试

不得引入新的视觉设计、Repository 直连、DAO/Entity/Database 类型或第二个消息写入者。

### Step 5.1 — Red 测试

在 `F:\xw\reverse-tutor-newmp\mobile-native\feature\chat\src\test\java\com\reversetutor\feature\chat\ChatUiStateTest.kt` 覆盖：

1. 持久化 user/assistant records 重建后按时间稳定排序，assistant 名称来自 session snapshot。
2. Queued/Running 映射 Pending。
3. `No model configured` 映射 NoModel。
4. 其他失败只显示白名单安全文案，不显示 URL、Authorization、`sk-` 或异常类名。
5. 重新进入同一 session 不带入上一 session 消息。

在 `F:\xw\reverse-tutor-newmp\mobile-native\app\src\test\java\com\reversetutor\preview\shell\AppNavigationStateTest.kt` 增加返回聊天页后 session id 保持不变的断言；已有等价断言则复用并记录，不重复添加。

### Step 5.2 — Red/Green 命令

```powershell
$env:ANDROID_HOME='E:\Android\Sdk'
$env:ANDROID_SDK_ROOT='E:\Android\Sdk'
$env:GRADLE_USER_HOME='E:\Android\Gradle\newmp'
.\gradlew.bat :feature:chat:testDebugUnitTest --tests "*.ChatUiStateTest" --tests "*.SessionAssistantHostBridgeTest" --console=plain --no-daemon
.\gradlew.bat :app:testDebugUnitTest --tests "*.AppNavigationStateTest" --console=plain --no-daemon
```

若直接通过，记录“已有状态投影满足 Task 5，0 个生产文件修改”；若失败，只修 Feature contract → UiState 映射，不改后端算法或数据层。随后运行：

```powershell
.\gradlew.bat :feature:chat:testDebugUnitTest :app:testDebugUnitTest --console=plain --no-daemon
```

预期：`BUILD SUCCESSFUL`。

---

## Task 6：统一验收

### Step 6.1 — Android 全量

```powershell
cd F:\xw\reverse-tutor-newmp\mobile-native
$env:ANDROID_HOME='E:\Android\Sdk'
$env:ANDROID_SDK_ROOT='E:\Android\Sdk'
$env:GRADLE_USER_HOME='E:\Android\Gradle\newmp'
.\gradlew.bat test :app:lint :app:assembleDebug --console=plain --no-daemon
```

预期：`BUILD SUCCESSFUL`，无 lint 错误。

### Step 6.2 — Python 回归

```powershell
cd F:\xw\reverse-tutor-newmp
py -m pytest -q --ignore=tests/test_project_homepage.py
```

预期基线：`510 passed, 28 skipped`；若无 Python 改动，也要说明是否执行及原因。

### Step 6.3 — 边界检查

```powershell
cd F:\xw\reverse-tutor-newmp
git diff --check
git diff --name-only -- mobile-native/core/model mobile-native/core/protocol mobile-native/core/llm mobile-native/core/data
git status --short --branch
```

报告必须区分冻结目录下的生产代码和测试文件，不能把测试文件存在描述成冻结生产代码变更。

### Step 6.4 — 一次主力真机验收

仅当 serial `9CN0223C27017326` 在线时执行：

1. 安装最新 debug APK。
2. 打开已有普通会话。
3. 发送一条消息，验证用户消息、Pending/Completed 或 NoModel。
4. 强制停止并重启，验证历史仍在且 session 未串线。
5. 不调用真实 Provider，不输入或记录 API key。

设备离线写 `not_run`，不得重试安装或以 JVM 代替设备证据。结束后执行：

```powershell
adb -s 9CN0223C27017326 uninstall com.reversetutor.preview.test
adb -s 9CN0223C27017326 shell pm enable --user 0 com.reversetutor.preview
adb -s 9CN0223C27017326 shell am start -W -n com.reversetutor.preview/.MainActivity
```

### Step 6.5 — 统一交付格式

Aily 完成后只返回一份报告，包含：

- Task 4、5、6 的 Red/Green 命令和真实结果；
- 修改文件绝对路径、每个修改理由、生产文件数量；
- 冻结目录生产代码与测试文件分别列出；
- Android/Python/Lint/assemble 结果；
- 真机 serial/API/场景/结果，或 `not_run` 原因；
- 未解决问题及下一项最小判别实验；
- 明确声明未 commit、未 push、未 tag。

完成后停止，等待主控统一验收；不要自行进入 V2 记忆拓扑。
