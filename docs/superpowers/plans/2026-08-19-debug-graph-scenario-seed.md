# Debug Graph Scenario Seed Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task with review checkpoints. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 Debug 首次启动时通过现有 Repository 幂等写入四个结构化会话及跨会话图谱数据，让全局图谱可直接人工浏览。

**Architecture:** `DebugGraphScenarioDefinition` 只保存纯 Kotlin 固定 fixture；`DebugGraphScenarioSeeder` 位于 app/wiring，按会话→消息→记忆→节点→边顺序调用现有 Repository；`MainActivity` 只在 Debug 启动协程中触发它。不会改 core:data、Room、协议或图谱算法。

**Tech Stack:** Kotlin、Android lifecycleScope、现有 Room-backed Repository、JUnit、Android instrumentation、ADB。

---

### Task 1: 创建纯数据场景定义和契约测试

**Files:**
- Create: `mobile-native/app/src/main/java/com/reversetutor/preview/wiring/DebugGraphScenarioDefinition.kt`
- Create: `mobile-native/app/src/test/java/com/reversetutor/preview/wiring/DebugGraphScenarioDefinitionTest.kt`

- [ ] **Step 1: 写失败测试，锁定四会话、消息、节点和边数量**

```kotlin
@Test
fun definitionContainsFourSessionsAndCrossSessionGraph() {
    val seed = DebugGraphScenarioDefinition.build()

    assertEquals(4, seed.sessions.size)
    assertEquals(8, seed.messages.size)
    assertEquals(20, seed.nodes.count { !it.hidden })
    assertEquals(1, seed.nodes.count { it.hidden && it.id == DebugGraphScenarioDefinition.MarkerId })
    assertEquals(25, seed.edges.size)
    assertTrue(seed.edges.all { edge ->
        seed.nodes.any { it.id == edge.fromNodeId } && seed.nodes.any { it.id == edge.toNodeId }
    })
    assertTrue(seed.edges.any { it.fromNodeId == "debug-node-python" && it.toNodeId == "debug-node-global-graph" })
    assertTrue(seed.edges.any { it.fromNodeId == "debug-node-qwen" && it.toNodeId == "debug-node-provider" })
}

@Test
fun everyEvidenceNodeHasMessageBackReference() {
    val seed = DebugGraphScenarioDefinition.build()
    val messageIds = seed.messages.mapTo(hashSetOf()) { it.id }

    assertTrue(seed.anchors.all { it.sourceMessageId in messageIds })
    assertTrue(seed.nodes.filter { it.sourceMemoryId != null }
        .all { node -> seed.anchors.any { "memory-${it.id}" == node.sourceMemoryId } })
}
```

- [ ] **Step 2: 运行测试确认定义尚不存在**

Run from `F:\xw\reverse-tutor\mobile-native`:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*.DebugGraphScenarioDefinitionTest" --console=plain
```

Expected: compilation failure because `DebugGraphScenarioDefinition` and its seed types do not exist.

- [ ] **Step 3: 添加纯 fixture 类型和固定场景**

在 `DebugGraphScenarioDefinition.kt` 定义 `DebugGraphScenarioSeed`、`DebugSessionSeed`、`DebugAnchorSeed`、`DebugGraphNodeSeed`，并实现 `build()`。返回固定 ID 的四会话、八条消息、一个隐藏 marker 节点、20 个可见节点和 25 条边。可见节点至少覆盖 `Concept`、`Requirement`、`Source`、`Session`、`Person`、`Other` 六类；消息是固定本地教学文本，不包含 URL、API key、真实用户数据或 Provider 响应。

- [ ] **Step 4: 运行纯定义测试**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*.DebugGraphScenarioDefinitionTest" --console=plain
```

Expected: `BUILD SUCCESSFUL`，数量、类型、边端点和证据反向引用断言通过。

- [ ] **Step 5: 提交纯 fixture 契约**

```powershell
git add -- mobile-native/app/src/main/java/com/reversetutor/preview/wiring/DebugGraphScenarioDefinition.kt mobile-native/app/src/test/java/com/reversetutor/preview/wiring/DebugGraphScenarioDefinitionTest.kt
git commit -m "test(app): define structured debug graph scenario"
```

### Task 2: 实现幂等 Repository seeder

**Files:**
- Create: `mobile-native/app/src/main/java/com/reversetutor/preview/wiring/DebugGraphScenarioSeeder.kt`
- Create: `mobile-native/app/src/test/java/com/reversetutor/preview/wiring/DebugGraphScenarioSeederTest.kt`

- [ ] **Step 1: 写 seeder 行为测试**

使用 app 层 `DebugGraphScenarioSink` 记录 Repository 写入顺序，测试首次返回 `Seeded`、第二次返回 `AlreadySeeded`，并断言调用顺序严格为 `sessions → messages → anchors → nodes → edges`。

- [ ] **Step 2: 运行测试确认 seeder 尚不存在**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*.DebugGraphScenarioSeederTest" --console=plain
```

Expected: compilation failure because seeder result/sink types do not exist。

- [ ] **Step 3: 实现 marker 检查和 Repository 写入**

`ensureSeeded(nowEpochMillis)` 先调用 `graphRepository.snapshot(SessionRepository.defaultSpaceId)`；若存在 `DebugGraphScenarioDefinition.MarkerId` 返回 `AlreadySeeded`。否则依次调用 `sessionRepository.saveSession`、`messageRepository.saveMessage`、`memoryRepository.createAnchor`、`graphRepository.saveNode`、`graphRepository.saveEdge`。任何 null 或异常返回 `Failed("debug_graph_seed_failed")`，不得记录原始文本或凭据。

- [ ] **Step 4: 运行 seeder 单测和 app 编译**

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:compileDebugKotlin --console=plain
```

Expected: `BUILD SUCCESSFUL`，幂等、顺序、失败安全测试通过。

- [ ] **Step 5: 提交 seeder**

```powershell
git add -- mobile-native/app/src/main/java/com/reversetutor/preview/wiring/DebugGraphScenarioSeeder.kt mobile-native/app/src/test/java/com/reversetutor/preview/wiring/DebugGraphScenarioSeederTest.kt
git commit -m "feat(app): seed structured debug graph scenario"
```

### Task 3: 接入 Debug 首次启动

**Files:**
- Modify: `mobile-native/app/src/main/java/com/reversetutor/preview/MainActivity.kt`
- Create: `mobile-native/app/src/androidTest/java/com/reversetutor/preview/DebugGraphScenarioSeedDeviceTest.kt`

- [ ] **Step 1: 在 MainActivity 增加 Debug-only 启动调用**

在 `appGraph` 创建后启动一个 `lifecycleScope.launch`，仅当 `BuildConfig.DEBUG` 为 true 时调用 `DebugGraphScenarioSeeder(appGraph).ensureSeeded(System.currentTimeMillis())`。调用不得阻塞 `setContent`，现有 LLM profile bootstrap 和后台恢复逻辑保持不变。

- [ ] **Step 2: 编写 Android 真实 Room 验证**

`DebugGraphScenarioSeedDeviceTest` 使用 `ApplicationProvider.getApplicationContext()` 创建 `HybridAppGraph`，调用 seeder 后断言四个固定会话存在、snapshot 节点数为 21（20 可见 + hidden marker）、边数为 25 且所有边端点存在；第二次调用后数量不变。

- [ ] **Step 3: 构建并运行定向设备测试**

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
adb -s emulator-5554 install -r app\build\outputs\apk\debug\app-debug.apk
adb -s emulator-5554 install -r app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
adb -s emulator-5554 shell am instrument -w -r -e class com.reversetutor.preview.DebugGraphScenarioSeedDeviceTest com.reversetutor.preview.test/androidx.test.runner.AndroidJUnitRunner
```

Expected: seed count and second-run幂等断言通过。测试结束后执行 `adb -s emulator-5554 shell am start -W -n com.reversetutor.preview/.MainActivity`，保持主应用前台。

- [ ] **Step 4: 提交启动接入和设备测试**

```powershell
git add -- mobile-native/app/src/main/java/com/reversetutor/preview/MainActivity.kt mobile-native/app/src/androidTest/java/com/reversetutor/preview/DebugGraphScenarioSeedDeviceTest.kt
git commit -m "feat(app): seed debug graph on preview startup"
```

### Task 4: 全局图谱人工验收与回归

**Files:**
- Modify: `tasks/native-graph-source-fidelity-validation.md`
- Modify: `F:\CodexHome\skills\reverse-tutor-development-guard\references\known-issues.md` only if a new reproducible device issue appears.

- [ ] **Step 1: 在模拟器启动应用并打开全局图谱**

```powershell
adb -s emulator-5554 shell svc power stayon true
adb -s emulator-5554 shell pm enable --user 0 com.reversetutor.preview
adb -s emulator-5554 shell am start -W -n com.reversetutor.preview/.MainActivity
```

通过“学习大脑/全局图谱”入口检查四个会话根节点、跨会话边、节点详情和聊天证据回跳。

- [ ] **Step 2: 在主力真机上安装并启动**

```powershell
adb -s 9CN0223C27017326 shell svc power stayon true
adb -s 9CN0223C27017326 shell pm enable --user 0 com.reversetutor.preview
adb -s 9CN0223C27017326 install -r app\build\outputs\apk\debug\app-debug.apk
adb -s 9CN0223C27017326 shell am start -W -n com.reversetutor.preview/.MainActivity
```

确认 Android 12 上图谱可见，测试结束后不执行 `force-stop`，应用保持前台。

- [ ] **Step 3: 运行图谱回归矩阵**

```powershell
adb -s emulator-5554 shell am instrument -w -r -e class com.reversetutor.preview.GraphInteractionContractDeviceTest,com.reversetutor.preview.Phase5GraphDeviceTest com.reversetutor.preview.test/androidx.test.runner.AndroidJUnitRunner
adb -s 9CN0223C27017326 shell am instrument -w -r -e class com.reversetutor.preview.GraphInteractionContractDeviceTest,com.reversetutor.preview.Phase5GraphDeviceTest com.reversetutor.preview.test/androidx.test.runner.AndroidJUnitRunner
```

Expected: emulator 9 tests、真机 9 tests 全部通过；真机详情卡允许使用存在性断言，不添加嵌套滚动。

- [ ] **Step 4: 更新验收报告并提交**

记录真实节点/边数量、两设备结果、重启不重复和清库后重建结果；执行：

```powershell
git diff --check
git diff --name-only -- mobile-native/core/model mobile-native/core/protocol mobile-native/core/llm mobile-native/core/data
git status --short --branch
git add -- tasks/native-graph-source-fidelity-validation.md
git commit -m "docs: record debug graph scenario validation"
```

冻结路径必须为空，`.aily_tmp/` 和 `.superpowers/brainstorm/` 不得加入提交。
