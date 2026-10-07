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
import com.reversetutor.feature.chat.FormalCustomTreeScreen
import com.reversetutor.feature.chat.FormalLearningPresets
import com.reversetutor.feature.chat.FormalPresetDetailScreen
import com.reversetutor.feature.chat.FormalPresetLibraryScreen
import com.reversetutor.preview.shell.ActivityAnnouncementDialog
import com.reversetutor.preview.shell.ChallengeRoute
import com.reversetutor.preview.theme.ReverseTutorTheme
import java.io.File
import java.io.FileOutputStream
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FormalBatch2ScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun captureChallenge() = capture("challenge-716-237.png") {
        ChallengeRoute(joined = false, onBack = {}, onJoin = {})
    }

    @Test
    fun captureChallengeDetail() = capture(
        fileName = "challenge-detail-716-379.png",
        includePlatformWindows = true
    ) {
        ChallengeRoute(joined = false, onBack = {}, onJoin = {}, initialShowDetails = true)
    }

    @Test
    fun captureAnnouncement() = capture(
        fileName = "announcement-716-470.png",
        includePlatformWindows = true
    ) {
        ActivityAnnouncementDialog(onDismiss = {}, onViewChallenge = {})
    }

    @Test
    fun capturePresetLibrary() = capture("preset-library-716-611.png") {
        FormalPresetLibraryScreen(onBack = {}, onCustom = {}, onPreset = {})
    }

    @Test
    fun captureCustomTree() = capture("custom-tree-716-494.png") {
        FormalCustomTreeScreen(onBack = {})
    }

    @Test fun captureMath() = capturePreset("formal-math-sprint")
    @Test fun capturePython() = capturePreset("formal-python-concepts")
    @Test fun captureIelts() = capturePreset("formal-ielts-speaking")
    @Test fun captureSpeech() = capturePreset("formal-speech-expression")
    @Test fun captureAptitude() = capturePreset("formal-aptitude-reasoning")
    @Test fun captureFrontend() = capturePreset("formal-frontend-explain")
    @Test fun captureChemistry() = capturePreset("formal-chemistry-lab")
    @Test fun captureMachineLearning() = capturePreset("formal-machine-learning")

    private fun capturePreset(presetId: String) {
        val preset = checkNotNull(FormalLearningPresets.byId(presetId))
        capture("preset-${preset.figmaNodeId.replace(':', '-')}.png") {
            FormalPresetDetailScreen(
                preset = preset,
                creating = false,
                error = null,
                onBack = {},
                onUsePreset = {}
            )
        }
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
        val directory = File(context.getExternalFilesDir(null), "formal-batch2").apply {
            check(mkdirs() || isDirectory)
        }
        FileOutputStream(File(directory, fileName)).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
    }

    private companion object {
        const val FixtureTag = "formal-batch2-fixture"
    }
}
