package com.reversetutor.preview.shell

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.reversetutor.core.model.MessageRole
import com.reversetutor.feature.chat.ChatComposerState
import com.reversetutor.feature.chat.ChatDeleteConfirmation
import com.reversetutor.feature.chat.ChatDeleteImpact
import com.reversetutor.feature.chat.ChatMemoryDraft
import com.reversetutor.feature.chat.ChatMemoryCategory
import com.reversetutor.feature.chat.ChatScreen
import com.reversetutor.feature.chat.ChatTimelineItem
import com.reversetutor.feature.chat.ChatUiState
import com.reversetutor.feature.chat.PendingChatMessageDeletion
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

class ChatMessageActionsContractDeviceTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun messageLongPressMetadataDialogsUndoAndRichControlsAreReachable() {
        val message = ChatTimelineItem(
            id = "message-1",
            spaceId = "space-1",
            role = MessageRole.Assistant,
            roleLabel = "林澈",
            text = "```kotlin\nval answer = 42\n```",
            createdAtEpochMillis = 1_000L,
            attachmentLabels = emptyList(),
            attachments = emptyList(),
            quoteLabel = null
        )
        compose.setContent {
            MaterialTheme {
                ChatScreen(
                    state = ChatUiState(
                        sessionTitle = "函数",
                        learnerName = "林澈",
                        learnerStatus = "学习者",
                        contextPath = "",
                        messages = listOf(message),
                        composer = ChatComposerState(text = ""),
                        pendingDeletion = PendingChatMessageDeletion("session-1", "deleted", 1L, 5_001L)
                    ),
                    onComposerTextChange = {},
                    onSendMessage = {},
                    onCancelQuote = {},
                    onCreateImageDraft = {},
                    onCancelImageDraft = {},
                    onMessageAction = { _, _ -> },
                    memoryDraft = ChatMemoryDraft("message-1", "space-1", "记忆正文"),
                    deleteConfirmation = ChatDeleteConfirmation(
                        "message-1", "space-1", "代码消息", ChatDeleteImpact()
                    )
                )
            }
        }

        compose.onNodeWithContentDescription("复制代码").assertIsDisplayed()
        compose.onNodeWithText("消息已删除").assertIsDisplayed()
        compose.onNodeWithText("撤销").assertIsDisplayed()
        compose.onNodeWithText("记住这条").assertIsDisplayed()
        compose.onNodeWithText("仅删除消息（默认）").assertIsDisplayed()
    }

    @Test
    fun longPressSheetContainsExactlyFiveActionsAndNoRegenerate() {
        val message = ChatTimelineItem(
            id = "message-2",
            spaceId = "space-1",
            role = MessageRole.User,
            roleLabel = "我",
            text = "消息正文",
            createdAtEpochMillis = 1_000L,
            attachmentLabels = emptyList(),
            attachments = emptyList(),
            quoteLabel = null
        )
        compose.setContent {
            MaterialTheme {
                ChatScreen(
                    state = ChatUiState("会话", "林澈", "学习者", "", listOf(message), ChatComposerState(text = "")),
                    onComposerTextChange = {},
                    onSendMessage = {},
                    onCancelQuote = {},
                    onCreateImageDraft = {},
                    onCancelImageDraft = {},
                    onMessageAction = { _, _ -> }
                )
            }
        }

        compose.onNodeWithText("消息正文").performTouchInput {
            down(center)
            advanceEventTime(700L)
            up()
        }
        listOf("复制", "引用回复", "记住这条", "定位关联资料", "删除消息").forEach {
            compose.onNodeWithText(it).assertIsDisplayed()
        }
        compose.onNodeWithText("重新生成").assertDoesNotExist()
        compose.onNodeWithText("复制").performClick()
        compose.onNodeWithText("消息正文").performClick()
        compose.onNodeWithText("1970-01-01", substring = true).assertIsDisplayed()
    }

    @Test
    fun inlineMarkdownRoutesLinkAndMessageGesturesWithoutDoubleDispatch() {
        val openedUrls = mutableListOf<String>()
        val message = ChatTimelineItem(
            id = "message-inline",
            spaceId = "space-1",
            role = MessageRole.Assistant,
            roleLabel = "林澈",
            text = "[链接](https://example.com) 普通区域 `inline` ${'$'}a+b${'$'}",
            createdAtEpochMillis = 1_000L,
            attachmentLabels = emptyList(),
            attachments = emptyList(),
            quoteLabel = null
        )
        compose.setContent {
            MaterialTheme {
                ChatScreen(
                    state = ChatUiState(
                        "会话", "林澈", "学习者", "", listOf(message), ChatComposerState(text = "")
                    ),
                    onComposerTextChange = {},
                    onSendMessage = {},
                    onCancelQuote = {},
                    onCreateImageDraft = {},
                    onCancelImageDraft = {},
                    onMessageAction = { _, _ -> },
                    onOpenExternalLink = openedUrls::add
                )
            }
        }

        val richBody = compose.onNodeWithText("链接 普通区域 inline a+b")
        compose.onAllNodesWithText("链接 普通区域 inline a+b").assertCountEquals(1)
        richBody.performTouchInput { click(Offset(5f, center.y)) }
        compose.runOnIdle { assertEquals(listOf("https://example.com"), openedUrls) }
        compose.onNodeWithText("1970-01-01", substring = true).assertDoesNotExist()

        richBody.performTouchInput { click(Offset(right - 5f, center.y)) }
        compose.onNodeWithText("1970-01-01", substring = true).assertIsDisplayed()
        richBody.performTouchInput {
            down(Offset(5f, center.y))
            advanceEventTime(700L)
            up()
        }
        compose.runOnIdle { assertEquals(listOf("https://example.com"), openedUrls) }
        compose.onNodeWithText("复制").assertIsDisplayed()
        compose.onNodeWithText("引用回复").assertIsDisplayed()
        compose.onNodeWithText("记住这条").assertIsDisplayed()
        compose.onNodeWithText("定位关联资料").assertIsDisplayed()
        compose.onNodeWithText("删除消息").assertIsDisplayed()
    }

    @Test
    fun sevenRememberCategoriesAndUnavailableLabelsRemainReachableOnPhoneWidth() {
        compose.setContent {
            MaterialTheme {
                Box(Modifier.width(280.dp)) {
                    ChatScreen(
                        state = ChatUiState(
                            "会话", "林澈", "学习者", "", emptyList(), ChatComposerState(text = "")
                        ),
                        onComposerTextChange = {},
                        onSendMessage = {},
                        onCancelQuote = {},
                        onCreateImageDraft = {},
                        onCancelImageDraft = {},
                        onMessageAction = { _, _ -> },
                        memoryDraft = ChatMemoryDraft(
                            messageId = "message-1",
                            spaceId = "space-1",
                            text = "约束内容",
                            category = ChatMemoryCategory.Constraint
                        )
                    )
                }
            }
        }

        listOf(
            "身份（不可用）",
            "事实（不可用）",
            "偏好（不可用）",
            "目标（不可用）",
            "计划（不可用）",
            "约束",
            "待跟进（不可用）"
        ).forEach { label -> compose.onNodeWithText(label).assertIsDisplayed() }
    }

    @Test
    fun failedFinalizeKeepsPendingDeletionAndExposesRetryInsteadOfSuccess() {
        compose.setContent {
            MaterialTheme {
                ChatScreen(
                    state = ChatUiState(
                        sessionTitle = "会话",
                        learnerName = "林澈",
                        learnerStatus = "学习者",
                        contextPath = "",
                        messages = emptyList(),
                        composer = ChatComposerState(text = ""),
                        pendingDeletion = PendingChatMessageDeletion("session-1", "message-1", 1L, 5_001L),
                        pendingDeletionRetryRequired = true
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

        compose.onNodeWithText("删除未完成").assertIsDisplayed()
        compose.onNodeWithText("重试").assertIsDisplayed()
        compose.onNodeWithText("消息已删除").assertDoesNotExist()
    }
}
