package com.reversetutor.preview

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reversetutor.core.data.DataModule
import com.reversetutor.core.data.background.BackgroundGenerationOutcome
import com.reversetutor.preview.background.BackgroundGenerationNotifier
import com.reversetutor.preview.background.BackgroundGenerationOutcomeHandler
import com.reversetutor.preview.shell.FormalDiagnosticsRoute
import com.reversetutor.preview.wiring.HybridAppGraph
import com.reversetutor.preview.theme.ReverseTutorTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FormalDiagnosticsDeviceTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun seedSafeGenerationDiagnostic() {
        runBlocking {
            DataModule.localDataWipeRepository(context).wipeLocalData(System.currentTimeMillis())
            BackgroundGenerationOutcomeHandler(
                appContext = context,
                notifierFactory = { NoOpNotifier },
                notificationsPermissionGranted = { false },
                clock = { 1_000L }
            ).handle(
                jobId = "device-diagnostic-job",
                outcome = BackgroundGenerationOutcome.Failed(
                    "Provider timeout with Authorization sk-test"
                ),
                sourceMessageId = null
            )
            check(
                DataModule.memoryRepository(context).snapshot().errors.any {
                    it.id == "diagnostic-background-device-diagnostic-job" &&
                        it.origin == com.reversetutor.core.model.ErrorLogOrigin.Generation &&
                        it.code == "background_generation_failed"
                }
            )
        }
    }

    @After
    fun cleanUpLocalData() {
        runBlocking {
            DataModule.localDataWipeRepository(context).wipeLocalData(System.currentTimeMillis())
        }
    }

    @Test
    fun generationDiagnosticAppearsInReportAndCopiesOnlySafeText() {
        composeRule.setContent {
            ReverseTutorTheme {
                FormalDiagnosticsRoute(
                    hybridAppGraph = HybridAppGraph.create(context),
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithText("生成诊断报告")
            .performScrollTo()
            .performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("诊断报告", substring = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("后台生成失败", substring = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithText("后台生成失败", substring = true).assertExists()
        composeRule.onNodeWithText("后台生成任务未完成", substring = true).assertExists()
        composeRule.onNodeWithText("复制摘要")
            .performClick()

        val clipboard = context.getSystemService(ClipboardManager::class.java)
        val copied = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
        assertTrue(copied.contains("诊断报告"))
        assertTrue(copied.contains("不包含 API Key"))
        assertFalse(copied.contains("Authorization", ignoreCase = true))
        assertFalse(copied.contains("Bearer", ignoreCase = true))
        assertFalse(copied.contains("sk-", ignoreCase = true))
        assertFalse(copied.contains("https://", ignoreCase = true))
    }

    private companion object {
        val NoOpNotifier = object : BackgroundGenerationNotifier {
            override fun notifyCompleted(jobId: String) = Unit
            override fun notifyFailed(jobId: String) = Unit
        }
    }
}
