package com.reversetutor.preview

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.reversetutor.feature.chat.FormalHomeScreen
import com.reversetutor.feature.chat.FormalHomeSessionUi
import com.reversetutor.feature.chat.FormalHomeUiState
import com.reversetutor.feature.chat.FormalJoinedChallengeUi
import com.reversetutor.feature.chat.FormalPublicContentUi
import com.reversetutor.feature.memory.FormalWeeklyScreen
import com.reversetutor.feature.memory.FormalWeeklyUiState
import com.reversetutor.feature.settings.FormalSettingsScreen
import com.reversetutor.feature.settings.FormalSettingsUiState
import com.reversetutor.preview.shell.CommunityRoute
import com.reversetutor.preview.theme.ReverseTutorTheme
import java.io.File
import java.io.FileOutputStream
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FormalBatch1ScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun captureFormalHome() {
        composeRule.setContent {
            FormalFixtureFrame {
                FormalHomeScreen(
                    state = formalHomeState(),
                    onPublicContentClick = {},
                    onSessionClick = {},
                    onOpenChallenge = {},
                    onShowNewSessionSheet = {},
                    onDismissNewSessionSheet = {},
                    onStartLearningSetup = {},
                    onOpenWeekly = {}
                )
            }
        }

        captureFixture("home-716-13.png")
    }

    @Test
    fun captureFormalHomeNewSessionSheet() {
        composeRule.setContent {
            FormalFixtureFrame {
                FormalHomeScreen(
                    state = formalHomeState().copy(isNewSessionSheetVisible = true),
                    onPublicContentClick = {},
                    onSessionClick = {},
                    onOpenChallenge = {},
                    onShowNewSessionSheet = {},
                    onDismissNewSessionSheet = {},
                    onStartLearningSetup = {},
                    onOpenWeekly = {}
                )
            }
        }

        captureFixture("home-new-session-716-72.png")
    }

    @Test
    fun captureFormalHomeJoinedChallenge() {
        composeRule.setContent {
            FormalFixtureFrame {
                FormalHomeScreen(
                    state = formalHomeState().copy(
                        challenge = FormalJoinedChallengeUi(
                            id = "python-21-days",
                            title = "21天学习挑战",
                            dayLabel = "挑战进行中 · 第 8 天",
                            todayPrompt = "今天：用三句话讲清楚机会成本",
                            progressFraction = 8f / 21f
                        )
                    ),
                    onPublicContentClick = {},
                    onSessionClick = {},
                    onOpenChallenge = {},
                    onShowNewSessionSheet = {},
                    onDismissNewSessionSheet = {},
                    onStartLearningSetup = {},
                    onOpenWeekly = {}
                )
            }
        }

        captureFixture("home-joined-challenge-716-159.png")
    }

    @Test
    fun captureFormalWeekly() {
        composeRule.setContent {
            FormalFixtureFrame {
                FormalWeeklyScreen(state = FormalWeeklyUiState.preview())
            }
        }

        captureFixture("weekly-717-1130.png")
    }

    @Test
    fun captureFormalWeeklyMiddle() = captureWeeklyAt(
        fileName = "weekly-middle-717-1197.png",
        initialFirstVisibleItemIndex = 3
    )

    @Test
    fun captureFormalWeeklyBottom() = captureWeeklyAt(
        fileName = "weekly-bottom-717-1278.png",
        initialFirstVisibleItemIndex = 4
    )

    @Test
    fun captureFormalWeeklyManualSelection() {
        composeRule.setContent {
            FormalFixtureFrame {
                FormalWeeklyScreen(
                    state = FormalWeeklyUiState.preview(),
                    initialFirstVisibleItemIndex = 3,
                    initialShowScopeSheet = true
                )
            }
        }

        captureFixture("weekly-manual-717-1366.png", includePlatformWindows = true)
    }

    @Test
    fun captureFormalWeeklyFourSelected() {
        val state = FormalWeeklyUiState.preview()
        composeRule.setContent {
            FormalFixtureFrame {
                FormalWeeklyScreen(
                    state = state.copy(selectedSessionIds = state.sessionOptions.mapTo(linkedSetOf()) { it.id }),
                    initialFirstVisibleItemIndex = 3,
                    initialShowScopeSheet = true
                )
            }
        }

        captureFixture("weekly-four-selected-809-52.png", includePlatformWindows = true)
    }

    @Test
    fun captureFormalCommunity() {
        composeRule.setContent {
            FormalFixtureFrame {
                CommunityRoute()
            }
        }

        captureFixture("community-718-438.png")
    }

    @Test
    fun captureFormalSettings() {
        composeRule.setContent {
            FormalFixtureFrame {
                FormalSettingsScreen(
                    state = FormalSettingsUiState(),
                    onBack = {},
                    onOpenLlmConfiguration = {},
                    onOpenStorage = {},
                    onOpenImportExport = {},
                    onOpenAbout = {}
                )
            }
        }

        captureFixture("settings-718-11.png")
    }

    private fun captureWeeklyAt(fileName: String, initialFirstVisibleItemIndex: Int) {
        composeRule.setContent {
            FormalFixtureFrame {
                FormalWeeklyScreen(
                    state = FormalWeeklyUiState.preview(),
                    initialFirstVisibleItemIndex = initialFirstVisibleItemIndex
                )
            }
        }

        captureFixture(fileName)
    }

    private fun captureFixture(fileName: String, includePlatformWindows: Boolean = false) {
        val bitmap = composeRule.captureFormalFixture(
            fixtureTag = FixtureTag,
            includePlatformWindows = includePlatformWindows
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "formal-batch1").apply {
            check(mkdirs() || isDirectory)
        }
        FileOutputStream(File(directory, fileName)).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
    }

    @Composable
    private fun FormalFixtureFrame(content: @Composable () -> Unit) {
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

    private fun formalHomeState(): FormalHomeUiState = FormalHomeUiState(
        publicContent = FormalPublicContentUi(
            id = "public-interest-link-safety",
            title = "陌生链接，先停一下",
            summary = "三个小对话，学会识别网络诈骗",
            publishedLabel = "7月12日 · Reverse Tutor",
            pageLabel = "1 / 4"
        ),
        sessions = listOf(
            FormalHomeSessionUi(
                id = "economics",
                title = "宏观经济学基础",
                summary = "上次停在：财政政策如何影响利率",
                timeLabel = "刚刚",
                updatedAtEpochMillis = 4L,
                pinned = true
            ),
            FormalHomeSessionUi(
                id = "finance",
                title = "财政扩张与利率",
                summary = "纠正了挤出效应成立的前提",
                timeLabel = "刚刚",
                updatedAtEpochMillis = 3L,
                pinned = false
            ),
            FormalHomeSessionUi(
                id = "transformer",
                title = "Transformer 注意力机制",
                summary = "用类比重新解释 Query 与 Key",
                timeLabel = "周六",
                updatedAtEpochMillis = 2L,
                pinned = false
            ),
            FormalHomeSessionUi(
                id = "python",
                title = "Python 装饰器与闭包",
                summary = "还需补充闭包的执行顺序",
                timeLabel = "周四",
                updatedAtEpochMillis = 1L,
                pinned = false
            )
        )
    )

    private companion object {
        const val FixtureTag = "formal-batch1-fixture"
    }
}
