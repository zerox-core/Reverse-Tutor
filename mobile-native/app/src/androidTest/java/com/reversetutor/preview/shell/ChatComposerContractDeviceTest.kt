package com.reversetutor.preview.shell

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.reversetutor.feature.chat.ChatAttachmentKind
import com.reversetutor.feature.chat.ChatAttachmentReadiness
import com.reversetutor.feature.chat.ChatComposerState
import com.reversetutor.feature.chat.ChatDraftAttachment
import com.reversetutor.feature.chat.ChatScreen
import com.reversetutor.feature.chat.ChatUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ChatComposerContractDeviceTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun headerDestinationsAttachmentSheetStableSendAndDragAreReachable() {
        var destination = ""
        var move: Pair<Int, Int>? = null
        compose.setContent {
            MaterialTheme {
                ChatScreen(
                    state = ChatUiState(
                        sessionTitle = "函数入门",
                        learnerName = "小六子",
                        learnerStatus = "会追问的学生",
                        contextPath = "",
                        messages = emptyList(),
                        composer = ChatComposerState(
                            text = "ready",
                            attachments = listOf(
                                readyAttachment("first", "first.png"),
                                readyAttachment("second", "second.pdf", ChatAttachmentKind.Source)
                            )
                        )
                    ),
                    onComposerTextChange = {},
                    onSendMessage = {},
                    onCancelQuote = {},
                    onCreateImageDraft = {},
                    onCancelImageDraft = {},
                    onMessageAction = { _, _ -> },
                    onOpenSearch = { destination = "search" },
                    onOpenContextHub = { destination = "graph" },
                    onMoveAttachment = { from, to -> move = from to to }
                )
            }
        }

        compose.onNodeWithContentDescription("资料与引用")
            .assertWidthIsAtLeast(44.dp)
            .assertHeightIsAtLeast(44.dp)
            .performClick()
        assertEquals("search", destination)
        compose.onNodeWithContentDescription("当前会话图谱")
            .assertWidthIsAtLeast(44.dp)
            .assertHeightIsAtLeast(44.dp)
            .performClick()
        assertEquals("graph", destination)

        compose.onNodeWithContentDescription("发送")
            .assertWidthIsEqualTo(44.dp)
            .assertHeightIsEqualTo(44.dp)
        compose.onNodeWithContentDescription("添加图片或资料")
            .assertWidthIsAtLeast(44.dp)
            .assertHeightIsAtLeast(44.dp)
            .performClick()
        listOf("选择图片", "选择应用内资料", "拍照", "查看本会话资料").forEach {
            compose.onNodeWithText(it).assertIsDisplayed()
        }
        compose.onNodeWithText("选择图片").performClick()

        compose.onNodeWithText("图片 · first.png").performTouchInput {
            down(center)
            advanceEventTime(700L)
            moveBy(Offset(120f, 0f), 200L)
            up()
        }
        assertEquals(0 to 1, move)
    }

    @Test
    fun emptyComposerSendIsSemanticallyDisabled() {
        compose.setContent {
            MaterialTheme {
                ChatScreen(
                    state = ChatUiState(
                        sessionTitle = "空草稿",
                        learnerName = "小六子",
                        learnerStatus = "学生",
                        contextPath = "",
                        messages = emptyList(),
                        composer = ChatComposerState("")
                    ),
                    onComposerTextChange = {},
                    onSendMessage = {},
                    onCancelQuote = {},
                    onCreateImageDraft = {},
                    onCancelImageDraft = {},
                    onMessageAction = { _, _ -> }
                )
            }
        }

        compose.onNodeWithContentDescription("发送")
            .assertIsNotEnabled()
            .assertWidthIsEqualTo(44.dp)
            .assertHeightIsEqualTo(44.dp)
    }

    private fun readyAttachment(
        id: String,
        name: String,
        kind: ChatAttachmentKind = ChatAttachmentKind.Image
    ) = ChatDraftAttachment(
        id = id,
        kind = kind,
        name = name,
        sourceId = if (kind == ChatAttachmentKind.Source) id else null,
        uri = if (kind == ChatAttachmentKind.Source) null else "content://$id",
        mimeType = if (kind == ChatAttachmentKind.Source) null else "image/png",
        readiness = ChatAttachmentReadiness.Ready
    )
}
