package com.reversetutor.preview

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.reversetutor.core.model.MessageRole
import com.reversetutor.feature.chat.ChatComposerState
import com.reversetutor.feature.chat.ChatScreen
import com.reversetutor.feature.chat.ChatTimelineItem
import com.reversetutor.feature.chat.ChatUiState
import com.reversetutor.preview.theme.ReverseTutorTheme
import java.io.File
import java.io.FileOutputStream
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FormalBatch3ScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun captureFormalChat() {
        composeRule.setContent {
            FormalFixtureFrame {
                ChatScreen(
                    state = formalChatState(),
                    onComposerTextChange = {},
                    onSendMessage = {},
                    onCancelQuote = {},
                    onCreateImageDraft = {},
                    onCancelImageDraft = {},
                    onMessageAction = { _, _ -> }
                )
            }
        }

        composeRule.waitForIdle()
        val bitmap = composeRule.onNodeWithTag(FixtureTag).captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "formal-batch3").apply {
            check(mkdirs() || isDirectory)
        }
        FileOutputStream(File(directory, "chat-716-1424.png")).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
    }

    @Composable
    private fun FormalFixtureFrame(content: @Composable () -> Unit) {
        ReverseTutorTheme {
            Box(
                modifier = Modifier
                    .size(width = 390.dp, height = 884.dp)
                    .testTag(FixtureTag)
            ) {
                content()
            }
        }
    }

    private fun formalChatState() = ChatUiState(
        sessionTitle = "宏观经济学基础",
        learnerName = "小P",
        learnerStatus = "正在学习",
        contextPath = "GDP 核算范围",
        messages = listOf(
            message(
                "student-restatement",
                MessageRole.Assistant,
                "我先按你的说法复述一遍：GDP 记录的是一个国家在一段时间内新产生的最终产品和服务价值。"
            ),
            message("teacher-question", MessageRole.User, "对。那为什么二手交易通常不计入 GDP？"),
            message(
                "student-question",
                MessageRole.Assistant,
                "因为二手物品不是本期新生产的，所以物品本身不重复计入。\n\n但交易平台收取的服务费属于本期服务，应该会计入，对吗？"
            ),
            message("tree-event", MessageRole.Tool, "世界树已整理 · GDP 核算范围"),
            message(
                "teacher-clarification",
                MessageRole.User,
                "是的。关键是区分“旧物价值”和这次交易中新产生的服务价值。"
            ),
            message(
                "student-check",
                MessageRole.Assistant,
                "明白了。以后判断是否计入 GDP，我会先问：这部分价值是不是在本期新产生的？"
            )
        ),
        composer = ChatComposerState(text = "")
    )

    private fun message(id: String, role: MessageRole, text: String) = ChatTimelineItem(
        id = id,
        spaceId = "formal-fixture",
        role = role,
        roleLabel = if (role == MessageRole.User) "我" else "小P",
        text = text,
        createdAtEpochMillis = 0L,
        attachmentLabels = emptyList(),
        attachments = emptyList(),
        quoteLabel = null
    )

    private companion object {
        const val FixtureTag = "formal-batch3-fixture"
    }
}
