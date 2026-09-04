package com.reversetutor.preview

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reversetutor.core.data.DataModule
import com.reversetutor.core.model.MessageRole
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * RT-2026-034 / I-034 · 真实 Activity 重启恢复设备测试。
 *
 * 生命周期契约：本类不持有任何自动启动 Activity 的 Compose 规则。使用
 * `createEmptyComposeRule`（不绑定单一生命周期、按注册层级查询语义树），
 * MainActivity 的启动与销毁全部由显式 [ActivityScenario] 管理：
 * “重启” = `scenario.close()`（Activity 真实 DESTROYED）→ 新 scenario
 * `launch()`（全新实例与全新组合根）。全程不使用 `ActivityScenario.recreate()`。
 *
 * 会话一律通过已验证的 UI 路径创建（与 Phase2CoreLoopDeviceTest 相同的
 * 选择器），不在测试中预先用 repository seed 会话：LazyColumn 列表项只有
 * 进入可视区域才会被组合，预 seed 的会话在列表首屏不可见会让文本等待超时。
 *
 * 验收场景（使用当前 Debug 配置的真实 runtime）：
 * 1. 生命周期 1：预设创建会话 → 无模型发送 → 安全失败「未配置模型」且无 Mock 回复；
 * 2. Profile 在 Activity 之外激活后重启 → 生命周期 2：打开同一会话 → 发送 →
 *    assistant 消息成功持久化（不依赖具体 Provider 文案）；
 * 3. 再次重启 → 生命周期 3：重新打开同一会话 → 两条用户消息与 assistant 消息仍可见；
 * 4. 三个生命周期绑定三个互不相同的 MainActivity 实例（证明是真实重启）。
 */
@RunWith(AndroidJUnit4::class)
class Phase2CoreLoopRestartDeviceTest {

    // The empty rule must be *applied* through the JUnit lifecycle so the test
    // environment is registered before any composition is created; the Activity
    // itself stays under explicit scenario control below.
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val presetTitle = "高三数学讲题冲刺"
    private val observedActivities = mutableListOf<MainActivity>()

    /** 同一时刻只允许持有一个 scenario；重启即 close 旧的再 launch 新的。 */
    private var scenario: ActivityScenario<MainActivity>? = null

    @After
    fun closeScenario() {
        scenario?.close()
        scenario = null
    }

    @Test
    fun assistantReplySurvivesRealActivityRestartAcrossFreshScenarios() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        wipeLocalData(context)
        clearAllLlmProfiles(context)

        // ---- 生命周期 1：预设创建会话 → 无模型发送 → 安全失败 ----
        observedActivities += launchFirstActivity()
        openChatWithBuiltInPreset()
        clearAllLlmProfiles(context) // 防 DebugLlmProfileBootstrapper 启动注入
        send("RT034-no-model")
        waitForText("RT034-no-model")
        waitForSubstring("未配置模型")
        assertTextAbsent("Mock generation ready")

        // ---- 真实 Debug 配置在新 Activity 启动时由 bootstrapper 恢复 ----
        val sessionId = sessionIdForPreset(context)
        val assistantCountBeforeRealGeneration = assistantMessageCount(context, sessionId)

        // ---- 生命周期 2：全新实例，重新打开同一会话 → 发送 → 真实 assistant 回复 ----
        observedActivities += restartWithFreshActivity()
        reopenBuiltInPresetSession()
        send("RT034-mock-generation")
        waitForAssistantMessage(context, sessionId, assistantCountBeforeRealGeneration + 1)
        composeRule.onNodeWithText("RT034-mock-generation").assertIsDisplayed()
        composeRule.onNodeWithText("RT034-no-model").assertIsDisplayed()

        // ---- 生命周期 3：再次重启后重新打开同一会话，持久化历史仍可见 ----
        observedActivities += restartWithFreshActivity()
        reopenBuiltInPresetSession()
        composeRule.onNodeWithText("RT034-no-model").assertIsDisplayed()
        composeRule.onNodeWithText("RT034-mock-generation").assertIsDisplayed()
        assertTrue(
            "真实 assistant 回复未在重启后持久化",
            assistantMessageCount(context, sessionId) >= assistantCountBeforeRealGeneration + 1
        )

        assertNotSame(
            "生命周期 2 必须是不同于生命周期 1 的 Activity 实例",
            observedActivities[0],
            observedActivities[1]
        )
        assertNotSame(
            "生命周期 3 必须是不同于生命周期 2 的 Activity 实例",
            observedActivities[1],
            observedActivities[2]
        )
        assertTrue(
            "预期 3 个互不相同的 MainActivity 实例",
            observedActivities.distinctBy { System.identityHashCode(it) }.size == 3
        )
    }

    // ---- lifecycle plumbing ----

    private fun launchFirstActivity(): MainActivity {
        check(scenario == null) { "only one scenario may be alive at a time" }
        return launchAndTrack()
    }

    private fun restartWithFreshActivity(): MainActivity {
        // close() 将当前 MainActivity 真实销毁（RT-2026-034 的修复要点：
        // 绝不在存活的测试规则层级上调用 recreate()），随后新 scenario
        // 以全新实例与全新组合根重启应用。
        scenario?.close()
        scenario = null
        return launchAndTrack()
    }

    private fun launchAndTrack(): MainActivity {
        val newScenario = ActivityScenario.launch(MainActivity::class.java)
        scenario = newScenario
        lateinit var activity: MainActivity
        newScenario.onActivity { activity = it }
        return activity
    }

    // ---- UI helpers（沿用 Phase2CoreLoopDeviceTest 已验证的选择器）----

    private fun openChatWithBuiltInPreset() {
        waitForText("会话")
        composeRule.onNodeWithTag("formal-home-new-session").performClick()
        waitForText("学习模式")
        composeRule.onNodeWithText("学习模式").performClick()
        dismissActivityAnnouncementIfPresent()
        waitForText("内置预设")
        composeRule.onNodeWithText(presetTitle).performClick()
        waitForText("使用此预设")
        composeRule.onNodeWithText("使用此预设").performClick()
        waitForText("创建并进入聊天")
        composeRule.onNodeWithText("创建并进入聊天").performClick()
        waitForChatComposer()
    }

    private fun reopenBuiltInPresetSession() {
        waitForText("会话")
        waitForText(presetTitle)
        composeRule.onAllNodesWithText(presetTitle).onFirst().performClick()
        waitForChatComposer()
    }

    private fun send(text: String) {
        composeRule.onAllNodes(hasSetTextAction()).onFirst().performTextInput(text)
        composeRule.onNodeWithContentDescription("发送").performClick()
    }

    private fun waitForText(text: String, timeoutMillis: Long = 15_000) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForSubstring(substring: String, timeoutMillis: Long = 15_000) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodes(hasText(substring, substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForChatComposer(timeoutMillis: Long = 15_000) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithContentDescription("发送").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun dismissActivityAnnouncementIfPresent() {
        composeRule.waitUntil(2_000) {
            composeRule.onAllNodesWithText("稍后再说").fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText("内置预设").fetchSemanticsNodes().isNotEmpty()
        }
        val dismissNodes = composeRule.onAllNodesWithText("稍后再说")
        if (dismissNodes.fetchSemanticsNodes().isNotEmpty()) {
            dismissNodes.onFirst().performClick()
        }
    }

    private fun assertTextAbsent(text: String) {
        val nodes = composeRule.onAllNodesWithText(text).fetchSemanticsNodes()
        check(nodes.isEmpty()) { "未预期出现文本：$text" }
    }

    private fun sessionIdForPreset(context: Context): String = runBlocking {
        DataModule.sessionRepository(context).listSessions()
            .first { it.title == presetTitle }
            .id
    }

    private fun assistantMessageCount(context: Context, sessionId: String): Int = runBlocking {
        DataModule.messageRepository(context).listMessages(sessionId)
            .count { it.role == MessageRole.Assistant && it.text.isNotBlank() }
    }

    private fun waitForAssistantMessage(
        context: Context,
        sessionId: String,
        expectedMinimum: Int,
        timeoutMillis: Long = 60_000
    ) {
        composeRule.waitUntil(timeoutMillis) {
            assistantMessageCount(context, sessionId) >= expectedMinimum
        }
    }

    // ---- repository helpers（只使用冻结的 DataModule 契约）----

    private fun wipeLocalData(context: Context) {
        runBlocking {
            DataModule.localDataWipeRepository(context)
                .wipeLocalData(System.currentTimeMillis())
        }
    }

    private fun clearAllLlmProfiles(context: Context) {
        runBlocking {
            val repository = DataModule.llmProfileRepository(context)
            repository.listProfiles().forEach { repository.deleteProfile(it.id) }
        }
    }
}
