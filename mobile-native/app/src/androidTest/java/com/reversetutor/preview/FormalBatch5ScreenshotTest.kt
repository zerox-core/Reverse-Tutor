package com.reversetutor.preview

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.reversetutor.core.llm.LlmProviderPreset
import com.reversetutor.feature.settings.FormalLlmConfigurationScreen
import com.reversetutor.feature.settings.FormalLlmProvider
import com.reversetutor.feature.settings.LlmProfileItem
import com.reversetutor.feature.settings.LlmProfileSettingsUiState
import com.reversetutor.preview.shell.FormalPersonalizationScreen
import com.reversetutor.preview.shell.FormalRoleGoalOverlay
import com.reversetutor.preview.shell.FormalRoleGoalScreen
import com.reversetutor.preview.shell.SessionLibrarySettingsScreen
import com.reversetutor.preview.theme.ReverseTutorTheme
import java.io.File
import java.io.FileOutputStream
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FormalBatch5ScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun captureSessionLibrary() = capture("session-library-717-1730.png") {
        SessionLibrarySettingsScreen(
            sessionTitle = "Python 概念讲解",
            onBack = {},
            onSelectDestination = {},
            onPickSource = {}
        )
    }

    @Test
    fun captureRoleGoalDefault() = capture("role-goal-717-1895.png") {
        FormalRoleGoalScreen("Python 概念讲解", {}, {})
    }

    @Test
    fun captureRoleGoalHighImpact() = capture(
        fileName = "role-goal-confirm-717-1961.png",
        includePlatformWindows = true
    ) {
        FormalRoleGoalScreen(
            sessionTitle = "Python 概念讲解",
            onBack = {},
            onSelectDestination = {},
            initialOverlay = FormalRoleGoalOverlay.HighImpactConfirmation
        )
    }

    @Test
    fun captureRoleGoalEvolutionNotice() = capture(
        fileName = "role-goal-evolution-717-2056.png",
        includePlatformWindows = true
    ) {
        FormalRoleGoalScreen(
            sessionTitle = "Python 概念讲解",
            onBack = {},
            onSelectDestination = {},
            initialOverlay = FormalRoleGoalOverlay.AutomaticEvolutionNotice
        )
    }

    @Test
    fun capturePersonalization() = capture("personalization-717-2140.png") {
        FormalPersonalizationScreen("Python 概念讲解", {}, {})
    }

    @Test
    fun captureDeepSeek() = capture("llm-deepseek-718-114.png") {
        FormalLlmConfigurationScreen(
            state = llmState(),
            onBack = {},
            onActivateProfile = {},
            onTestProfile = {},
            initialProvider = FormalLlmProvider.DeepSeek
        )
    }

    @Test
    fun captureKimi() = capture("llm-kimi-718-199.png") {
        FormalLlmConfigurationScreen(
            state = llmState(),
            onBack = {},
            onActivateProfile = {},
            onTestProfile = {},
            initialProvider = FormalLlmProvider.Kimi
        )
    }

    @Test
    fun captureProviderPresets() = capture("llm-presets-718-273.png") {
        FormalLlmConfigurationScreen(
            state = llmState(),
            onBack = {},
            onActivateProfile = {},
            onTestProfile = {},
            initialShowPresets = true
        )
    }

    private fun capture(
        fileName: String,
        includePlatformWindows: Boolean = false,
        content: @Composable () -> Unit
    ) {
        composeRule.setContent {
            ReverseTutorTheme {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 390.dp, height = 884.dp)
                            .testTag(FixtureTag)
                    ) {
                        content()
                    }
                }
            }
        }
        val bitmap = composeRule.captureFormalFixture(
            fixtureTag = FixtureTag,
            includePlatformWindows = includePlatformWindows
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "formal-batch5").apply {
            check(mkdirs() || isDirectory)
        }
        FileOutputStream(File(directory, fileName)).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
    }

    private fun llmState() = LlmProfileSettingsUiState(
        summary = "3 个模型配置",
        presetLabels = LlmProviderPreset.defaults.map { it.label },
        profileItems = listOf(
            LlmProfileItem(
                id = "deepseek-personal",
                name = "个人账户",
                providerModelLabel = "DeepSeek · deepseek-chat",
                baseUrlLabel = "https://api.deepseek.com/v1",
                keyStatusLabel = "•••• 91D8",
                active = true
            ),
            LlmProfileItem(
                id = "deepseek-backup",
                name = "备用账户",
                providerModelLabel = "DeepSeek · deepseek-reasoner",
                baseUrlLabel = "https://api.deepseek.com/v1",
                keyStatusLabel = "•••• 7A20",
                active = false
            ),
            LlmProfileItem(
                id = "kimi-main",
                name = "主账户",
                providerModelLabel = "Kimi · moonshot-v1-128k",
                baseUrlLabel = "https://api.moonshot.cn/v1",
                keyStatusLabel = "•••• A672",
                active = true
            )
        ),
        connectionStatusLabel = "连接正常",
        presets = LlmProviderPreset.defaults
    )

    private companion object {
        const val FixtureTag = "formal-batch5-fixture"
    }
}
