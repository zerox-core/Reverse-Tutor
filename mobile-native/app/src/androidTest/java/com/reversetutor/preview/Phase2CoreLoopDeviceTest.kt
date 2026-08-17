package com.reversetutor.preview

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reversetutor.core.data.DataModule
import com.reversetutor.core.data.llm.LlmProfileInput
import com.reversetutor.core.model.LlmProviderKind
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * NATIVE-P2-006 设备验收：核心教学闭环。
 *
 * 设备测试契约基于当前真实中文界面文案与 contentDescription，不依赖英文占位文本，
 * 也不修改产品文案。覆盖：会话首页、新建会话、预设创建、打开会话、空消息、发送、
 * 无模型提示、Fake Runtime 回复、重启后的持久化，以及会话隔离与返回导航。
 *
 * 仅在 emulator-5554 上运行；全程使用 FakeLlmGenerationRuntime，不调用真实 Provider。
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class Phase2CoreLoopDeviceTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val presetTitleA = "高三数学讲题冲刺"
    private val presetTitleB = "Python 概念讲解"

    @Before
    fun resetLocalState() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        runBlocking {
            DataModule.localDataWipeRepository(context).wipeLocalData(System.currentTimeMillis())
        }
        composeRule.activityRule.scenario.recreate()
        waitForText("会话")
    }

    /**
     * 核心闭环：新建会话 → 预设创建 → 打开会话 → 空消息 → 发送（无模型）→
     * 未配置模型提示 → 注入 Fake Runtime → 发送 → Mock 回复 → 重启持久化。
     */
    @Test
    fun a_coreLoopPersistsNoModelAndMockReplyAfterRestart() {
        // 会话首页
        waitForText("会话")

        // 新建会话：点击「新建会话」按钮（contentDescription）→ 弹出模式选择
        composeRule.onNodeWithTag("formal-home-new-session").performClick()
        // 进入学习预设页（点击「学习模式」卡片触发 onStartLearningSetup）
        waitForText("学习模式")
        composeRule.onNodeWithText("学习模式").performClick()

        // 预设创建：选择预设 A → 使用这个预设
        dismissActivityAnnouncementIfPresent()
        waitForText("内置预设")
        composeRule.onNodeWithText(presetTitleA).performClick()
        waitForText("使用此预设")
        composeRule.onNodeWithText("使用此预设").performClick()
        waitForText("创建并进入聊天")
        composeRule.onNodeWithText("创建并进入聊天").performClick()

        // 打开会话：创建后自动进入聊天页
        waitForContentDescription("返回会话首页")

        // 预设会话创建后持久化对应的助手开场消息
        waitForSubstring("我是小岚")

        // Debug build can bootstrap local profiles from local.properties on activity
        // startup. The no-model branch must explicitly remove those test-local
        // profiles before sending, otherwise it is not a no-model scenario.
        clearAllLlmProfiles()

        // 发送（无模型配置）
        composeRule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("P2-006-no-model")
        composeRule.onNodeWithContentDescription("发送").performClick()
        waitForText("P2-006-no-model")
        // 无模型提示：生成状态行展示「未配置模型」，且没有 Mock 回复
        waitForSubstring("未配置模型")
        composeRule.onAllNodesWithText("Mock generation ready").assertCountEquals(0)

        // 注入 Fake Runtime（激活一个本地模型 Profile）
        createActivePreviewProfile()
        restartAtSessionsHome()
        waitForText(presetTitleA)
        composeRule.onAllNodesWithText(presetTitleA).onFirst().performClick()
        waitForContentDescription("返回会话首页")

        // 发送 → Fake Runtime 回复
        composeRule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("P2-006-mock-generation")
        composeRule.onNodeWithContentDescription("发送").performClick()
        waitForText("Mock generation ready")
        composeRule.onNodeWithText("P2-006-mock-generation").assertIsDisplayed()

        // 重启后的持久化
        composeRule.activityRule.scenario.recreate()
        waitForText("会话")
        waitForText(presetTitleA)
        // 重新打开该会话
        composeRule.onAllNodesWithText(presetTitleA).onFirst().performClick()
        waitForContentDescription("返回会话首页")
        waitForText("P2-006-no-model")
        composeRule.onNodeWithText("P2-006-mock-generation").assertIsDisplayed()
        composeRule.onNodeWithText("Mock generation ready").assertIsDisplayed()
    }

    /**
     * 会话隔离：在会话 A 发送消息并获得回复后，进入会话 B 发送消息；
     * 再切回 A，确认 B 的消息与回复不会进入 A，A 的内容仍然存在。
     */
    @Test
    fun b_sessionIsolationDoesNotLeakAcrossSessions() {
        createActivePreviewProfile()
        restartAtSessionsHome()

        // 创建会话 A
        openHomeAndCreateSession(presetTitleA)
        waitForContentDescription("返回会话首页")
        composeRule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("P2-ISO-A")
        composeRule.onNodeWithContentDescription("发送").performClick()
        waitForText("P2-ISO-A")
        waitForText("Mock generation ready")

        // 返回会话首页
        composeRule.onNodeWithContentDescription("返回会话首页").performClick()
        waitForText("会话")

        // 创建会话 B
        openHomeAndCreateSession(presetTitleB)
        waitForContentDescription("返回会话首页")
        composeRule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("P2-ISO-B")
        composeRule.onNodeWithContentDescription("发送").performClick()
        waitForText("P2-ISO-B")
        waitForText("Mock generation ready")

        // 返回会话首页，重新打开 A
        composeRule.onNodeWithContentDescription("返回会话首页").performClick()
        waitForText("会话")
        waitForText(presetTitleA)
        composeRule.onAllNodesWithText(presetTitleA).onFirst().performClick()
        waitForContentDescription("返回会话首页")

        // A 中仍存在自己的消息，且不包含 B 的消息
        waitForText("P2-ISO-A")
        assertTextNotPresent("P2-ISO-B")
    }

    /**
     * 返回导航：Chat 返回会话首页；从 Chat 进入 Context（学习大脑）后返回 Chat，
     * 当前会话状态不丢失；再次返回回到会话首页。
     */
    @Test
    fun c_backNavigationPreservesChatState() {
        createActivePreviewProfile()
        restartAtSessionsHome()

        openHomeAndCreateSession(presetTitleA)
        waitForContentDescription("返回会话首页")

        // Chat -> ContextHub（学习大脑）
        composeRule.onNodeWithContentDescription("当前会话图谱").performClick()
        // Wait for navigation to commit before dispatching system back; otherwise the
        // root back handler still observes the previous Chat destination.
        waitForTag("context-return-chat")
        // ContextHub 返回 Chat（系统返回由 AppNavigation.handleSystemBack 处理）
        composeRule.activity.onBackPressedDispatcher.onBackPressed()
        waitForContentDescription("返回会话首页")
        // 当前会话标题仍在
        composeRule.onNodeWithText(presetTitleA).assertIsDisplayed()

        // Chat -> 会话首页
        composeRule.activity.onBackPressedDispatcher.onBackPressed()
        waitForText("会话")
        waitForText(presetTitleA)
    }

    // ---- helpers ----

    private fun openHomeAndCreateSession(presetTitle: String) {
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
    }

    private fun restartAtSessionsHome() {
        composeRule.activityRule.scenario.recreate()
        waitForText("会话")
    }

    private fun waitForText(text: String, timeoutMillis: Long = 10_000) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForSubstring(substring: String, timeoutMillis: Long = 10_000) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodes(hasText(substring, substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForContentDescription(desc: String, timeoutMillis: Long = 10_000) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithContentDescription(desc).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForTag(tag: String, timeoutMillis: Long = 10_000) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
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

    private fun assertTextNotPresent(text: String) {
        val nodes = composeRule.onAllNodesWithText(text).fetchSemanticsNodes()
        check(nodes.isEmpty()) { "未预期出现文本：$text（会话隔离失败）" }
    }

    private fun createActivePreviewProfile() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        runBlocking {
            val repository = DataModule.llmProfileRepository(context)
            repository.saveProfile(
                input = LlmProfileInput(
                    name = "P2Profile",
                    provider = LlmProviderKind.OpenAiCompatible,
                    model = "gpt-4o-mini",
                    baseUrl = "https://api.openai.com/v1",
                    apiKey = ""
                ),
                nowEpochMillis = System.currentTimeMillis()
            )
            val profile = repository.listProfiles().first { it.name == "P2Profile" }
            repository.activateProfile(
                profileId = profile.id,
                nowEpochMillis = System.currentTimeMillis()
            )
        }
    }

    private fun clearAllLlmProfiles() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        runBlocking {
            val repository = DataModule.llmProfileRepository(context)
            repository.listProfiles().forEach { repository.deleteProfile(it.id) }
        }
    }
}
