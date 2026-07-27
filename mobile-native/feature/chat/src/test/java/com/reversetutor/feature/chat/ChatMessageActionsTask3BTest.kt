package com.reversetutor.feature.chat

import com.reversetutor.core.model.MessageRole
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMessageActionsTask3BTest {
    @Test
    fun longPressActionsAreExactlyTheFiveConfirmedActions() {
        assertEquals(
            listOf("复制", "引用回复", "记住这条", "定位关联资料", "删除消息"),
            ChatMessageAction.entries.map(ChatMessageAction::label)
        )
    }

    @Test
    fun quoteIdentityAndTextSurviveDraftPersistence() {
        val draft = ChatComposerDraft(
            text = "继续解释",
            quote = ChatQuoteTarget(
                messageId = "message-1",
                excerpt = "原始文本",
                sourceIdentity = "林澈"
            )
        )

        assertEquals(draft, ChatDraftCodec.decode(ChatDraftCodec.encode(draft)))
    }

    @Test
    fun deleteHidesOnlySelectedMessageAndUndoUsesExactFiveSecondBoundary() = runTest {
        val store = RecordingPendingDeletionStore()
        val deleted = mutableListOf<String>()
        val coordinator = ChatMessageDeletionCoordinator(
            store = store,
            deletePort = ChatMessageDeletePort { pendingDelete ->
                deleted += pendingDelete.messageId
                ChatMessageDeleteResult.Success
            }
        )

        val pending = (coordinator.request(
            sessionId = "session-1",
            messageId = "message-2",
            nowEpochMillis = 1_000L
        ) as ChatDeletionRequestResult.Accepted).pending
        assertEquals(6_000L, pending.expiresAtEpochMillis)
        assertEquals("message-2", store.load("session-1")?.messageId)
        assertEquals(ChatUndoDeletionResult.Undone, coordinator.undo("session-1", 5_999L))
        assertTrue(deleted.isEmpty())

        coordinator.request("session-1", "message-2", 10_000L)
        assertEquals(ChatUndoDeletionResult.Expired, coordinator.undo("session-1", 15_000L))
        assertEquals(ChatFinalizeDeletionResult.Success, coordinator.finalize("session-1", 15_000L))
        assertEquals(listOf("message-2"), deleted)
        assertEquals(ChatFinalizeDeletionResult.NothingPending, coordinator.finalize("session-1", 16_000L))
        assertEquals(listOf("message-2"), deleted)
    }

    @Test
    fun repeatedDeleteRequestDoesNotExtendDeadlineAndRestoredPendingStateFinalizes() = runTest {
        val store = RecordingPendingDeletionStore()
        val deleted = mutableListOf<String>()
        val firstCoordinator = ChatMessageDeletionCoordinator(store, ChatMessageDeletePort {
            deleted += it.messageId
            ChatMessageDeleteResult.Success
        })

        val first = firstCoordinator.request("session-1", "message-1", 20L)
        val repeated = firstCoordinator.request("session-1", "message-1", 4_000L)
        assertEquals(first, repeated)

        val restoredCoordinator = ChatMessageDeletionCoordinator(store, ChatMessageDeletePort {
            deleted += it.messageId
            ChatMessageDeleteResult.Success
        })
        assertEquals(ChatFinalizeDeletionResult.NotDue, restoredCoordinator.finalize("session-1", 5_019L))
        assertEquals(ChatFinalizeDeletionResult.Success, restoredCoordinator.finalize("session-1", 5_020L))
        assertEquals(listOf("message-1"), deleted)
    }

    @Test
    fun secondDeleteIsBlockedWhileAnyUndoWindowIsPending() {
        val store = RecordingPendingDeletionStore()
        val coordinator = ChatMessageDeletionCoordinator(
            store,
            ChatMessageDeletePort { ChatMessageDeleteResult.Success }
        )

        val first = coordinator.request("session-1", "message-1", 100L)
        val second = coordinator.request("session-2", "message-2", 200L)

        assertTrue(first is ChatDeletionRequestResult.Accepted)
        assertEquals(
            ChatDeletionRequestResult.AlreadyPending((first as ChatDeletionRequestResult.Accepted).pending),
            second
        )
        assertEquals(listOf("message-1"), store.loadAll().map { it.messageId })
    }

    @Test
    fun retryableOrThrowingDeleteKeepsPendingForRetry() = runTest {
        val store = RecordingPendingDeletionStore()
        val retryCoordinator = ChatMessageDeletionCoordinator(
            store,
            ChatMessageDeletePort { ChatMessageDeleteResult.RetryableFailure }
        )
        retryCoordinator.request("session-1", "message-1", 0L)

        assertEquals(
            ChatFinalizeDeletionResult.RetryableFailure,
            retryCoordinator.finalize("session-1", 5_000L)
        )
        assertEquals("message-1", store.load("session-1")?.messageId)

        val throwingCoordinator = ChatMessageDeletionCoordinator(store, ChatMessageDeletePort { error("storage") })
        assertEquals(
            ChatFinalizeDeletionResult.RetryableFailure,
            throwingCoordinator.finalize("session-1", 5_001L)
        )
        assertEquals("message-1", store.load("session-1")?.messageId)
    }

    @Test
    fun alreadyAbsentAfterCrashBeforePendingClearConvergesAndReleasesGlobalGate() = runTest {
        val store = CrashBeforeFirstClearPendingDeletionStore()
        store.save(PendingChatMessageDeletion("session-1", "message-1", 0L, 5_000L))
        var attempt = 0
        val deletePort = ChatMessageDeletePort {
            attempt += 1
            if (attempt == 1) ChatMessageDeleteResult.Success else ChatMessageDeleteResult.AlreadyAbsent
        }
        val sweep = ChatPendingDeletionSweeper(store, deletePort)
        val coordinator = ChatMessageDeletionCoordinator(store, deletePort)

        assertEquals(
            ChatPendingSweepResult(failedMessageIds = listOf("message-1")),
            sweep.sweep(5_000L)
        )
        assertEquals("message-1", store.load("session-1")?.messageId)
        assertTrue(coordinator.request("session-2", "message-2", 5_001L) is ChatDeletionRequestResult.AlreadyPending)

        assertEquals(ChatPendingSweepResult(), sweep.sweep(5_001L))
        assertTrue(store.loadAll().isEmpty())
        assertTrue(coordinator.request("session-2", "message-2", 5_002L) is ChatDeletionRequestResult.Accepted)
    }

    @Test
    fun coldStartSweepSchedulesFutureAndFinalizesExpiredWithFailureRetry() = runTest {
        val store = RecordingPendingDeletionStore()
        store.save(PendingChatMessageDeletion("session-1", "future", 1_000L, 6_000L))
        var deletes = 0
        val sweep = ChatPendingDeletionSweeper(store, ChatMessageDeletePort {
            deletes += 1
            if (deletes > 1) ChatMessageDeleteResult.Success else ChatMessageDeleteResult.RetryableFailure
        })

        assertEquals(ChatPendingSweepResult(nextSweepAtEpochMillis = 6_000L), sweep.sweep(5_999L))
        assertEquals(0, deletes)
        assertEquals(
            ChatPendingSweepResult(failedMessageIds = listOf("future")),
            sweep.sweep(6_000L)
        )
        assertEquals("future", store.load("session-1")?.messageId)
        assertEquals(ChatPendingSweepResult(), sweep.sweep(6_001L))
        assertTrue(store.loadAll().isEmpty())
    }

    @Test
    fun coldStartSweepEnumeratesEverySessionAndRetainsOnlyFailedFinalizations() = runTest {
        val store = RecordingPendingDeletionStore().apply {
            save(PendingChatMessageDeletion("session-a", "fails", 0L, 5_000L))
            save(PendingChatMessageDeletion("session-b", "deletes", 1L, 5_000L))
        }
        val attempted = mutableListOf<String>()
        val sweep = ChatPendingDeletionSweeper(store, ChatMessageDeletePort { pendingDelete ->
            attempted += pendingDelete.messageId
            if (pendingDelete.messageId == "deletes") {
                ChatMessageDeleteResult.Success
            } else {
                ChatMessageDeleteResult.RetryableFailure
            }
        })

        assertEquals(
            ChatPendingSweepResult(failedMessageIds = listOf("fails")),
            sweep.sweep(5_000L)
        )
        assertEquals(listOf("fails", "deletes"), attempted)
        assertEquals(listOf("fails"), store.loadAll().map { it.messageId })
    }

    @Test
    fun derivativeDeletionIsGatedWhenAtomicRestoreDoesNotExist() {
        val impact = ChatDeleteImpact(
            memories = listOf(ChatDerivative("memory-1", "随笔：函数定义", ChatDerivativeKind.Memory)),
            graphItems = listOf(ChatDerivative("node-1", "图谱：函数", ChatDerivativeKind.Graph)),
            supportsAtomicDerivativeDeleteAndRestore = false
        )

        assertTrue(impact.hasDerivatives)
        assertFalse(impact.canDeleteDerivatives)
        assertFalse(impact.canDeleteMessageOnly)
        assertEquals("后端未提供派生内容溯源标记，当前不能安全删除这条消息。", impact.deletionBoundary)
    }

    @Test
    fun memoryEditorShowsCompleteCategoriesAndOnlyExactMappingsAreSupported() {
        assertEquals(
            listOf("身份", "事实", "偏好", "目标", "计划", "约束", "待跟进"),
            ChatMemoryCategory.entries.map { it.label }
        )
        assertEquals(
            listOf(ChatMemoryCategory.Constraint),
            ChatMemoryCategory.entries.filter { it.capability is ChatMemoryCategoryCapability.Supported }
        )
        ChatMemoryCategory.entries
            .filterNot { it == ChatMemoryCategory.Constraint }
            .forEach { category ->
                val unavailable = category.capability as ChatMemoryCategoryCapability.Unavailable
                assertTrue(unavailable.reason.isNotBlank())
            }
    }

    @Test
    fun rememberedMetadataMapsIntoTimelineState() {
        val state = ChatUiState.from(
            sessionTitle = "会话",
            records = listOf(messageRecord("remembered"), messageRecord("plain")),
            composer = ChatComposerState(text = ""),
            rememberedMessageIds = setOf("remembered")
        )

        assertTrue(state.messages.first { it.id == "remembered" }.remembered)
        assertFalse(state.messages.first { it.id == "plain" }.remembered)
    }

    @Test
    fun sourceMappingKeepsOriginalNameAndOffersSessionReselectWhenInvalid() {
        val invalid = resolveChatSourceAttachment(
            attachment = ChatAttachmentUi(
                name = "原始讲义.pdf",
                mimeType = "application/pdf",
                uri = null,
                sourceId = "missing-source"
            ),
            sources = emptyList()
        )

        assertEquals("原始讲义.pdf", invalid.displayName)
        assertEquals("资料已失效", invalid.stateLabel)
        assertTrue(invalid.canReselectForCurrentSession)
        assertEquals(
            ChatInvalidSourceReselectRequest("session-1", "missing-source", "原始讲义.pdf"),
            invalid.toReselectRequest("session-1")
        )
    }

    @Test
    fun richParserRecognizesBlocksAndMalformedInputFallsBackToReadableText() {
        val parsed = ChatRichContentParser.parse(
            """# 标题

- 条目
> 引用

| 名称 | 值 |
| --- | --- |
| x | 1 |

```kotlin
val x = 1
```

${'$'}${'$'}x^2 + y^2${'$'}${'$'}

含有 `inline`、[链接](https://example.com) 和 ${'$'}a+b${'$'}。
""".trimIndent()
        )

        assertTrue(parsed.any { it is ChatRichBlock.Heading })
        assertTrue(parsed.any { it is ChatRichBlock.ListItem })
        assertTrue(parsed.any { it is ChatRichBlock.Quote })
        assertTrue(parsed.any { it is ChatRichBlock.Table })
        assertTrue(parsed.any { it is ChatRichBlock.Code })
        assertTrue(parsed.any { it is ChatRichBlock.Formula })
        assertTrue(parsed.filterIsInstance<ChatRichBlock.Paragraph>().flatMap { it.inlines }
            .any { it is ChatRichInline.Link })

        val malformed = "```kotlin\nval answer = 42"
        assertEquals(listOf(ChatRichBlock.PlainText(malformed)), ChatRichContentParser.parse(malformed))
    }

    @Test
    fun richInlineParagraphBuildsOneNaturalTextRunWithLinkAnnotation() {
        val paragraph = ChatRichContentParser.parse(
            "含有 `inline`、[链接](https://example.com) 和 ${'$'}a+b${'$'}。"
        ).single() as ChatRichBlock.Paragraph

        val annotated = buildRichInlineAnnotatedString(paragraph.inlines)

        assertEquals("含有 inline、链接 和 a+b。", annotated.text)
        assertEquals(
            "https://example.com",
            annotated.getStringAnnotations(
                tag = ChatRichInlineLinkTag,
                start = 0,
                end = annotated.length
            ).single().item
        )
    }

    @Test
    fun richInlineTapRoutingUsesExactUrlOffsetsAndLeavesOtherOffsetsForMessageTap() {
        val paragraph = ChatRichContentParser.parse(
            "前文 [链接](https://example.com) 后文"
        ).single() as ChatRichBlock.Paragraph
        val annotated = buildRichInlineAnnotatedString(paragraph.inlines)
        val linkStart = annotated.text.indexOf("链接")

        assertEquals(
            ChatRichInlineTapTarget.Link("https://example.com"),
            resolveChatRichInlineTapTarget(annotated, linkStart)
        )
        assertEquals(
            ChatRichInlineTapTarget.Link("https://example.com"),
            resolveChatRichInlineTapTarget(annotated, linkStart + 1)
        )
        assertEquals(
            ChatRichInlineTapTarget.Message,
            resolveChatRichInlineTapTarget(annotated, linkStart - 1)
        )
        assertEquals(
            ChatRichInlineTapTarget.Message,
            resolveChatRichInlineTapTarget(annotated, linkStart + "链接".length)
        )
        assertEquals(
            ChatRichInlineTapTarget.Message,
            resolveChatRichInlineTapTarget(annotated, annotated.length)
        )
    }

    @Test
    fun timelineAddsLightweightDateSeparatorsAndTruthfulTemporaryMetadata() {
        val first = timelineItem("one", 0L)
        val sameDay = timelineItem("two", 60_000L)
        val nextDay = timelineItem("three", 86_400_000L)

        val entries = buildChatTimelineEntries(listOf(first, sameDay, nextDay), timeZoneId = "UTC")

        assertEquals(2, entries.count { it is ChatTimelineEntry.DateSeparator })
        val metadata = chatMessageMetadata(first, timeZoneId = "UTC")
        assertTrue(metadata.exactTime.contains("1970"))
        assertEquals("已保存到本机", metadata.deliveryLabel)
    }

    private fun timelineItem(id: String, timestamp: Long) = ChatTimelineItem(
        id = id,
        spaceId = "space-1",
        role = MessageRole.User,
        roleLabel = "我",
        text = id,
        createdAtEpochMillis = timestamp,
        attachmentLabels = emptyList(),
        attachments = emptyList(),
        quoteLabel = null
    )

    private fun messageRecord(id: String) = com.reversetutor.core.data.message.MessageRecord(
        message = com.reversetutor.core.model.Message(
            id = id,
            spaceId = "space-1",
            sessionId = "session-1",
            role = MessageRole.User,
            text = id,
            createdAtEpochMillis = 1L
        ),
        quote = null
    )
}

private class RecordingPendingDeletionStore : ChatPendingDeletionStore {
    private val values = mutableMapOf<String, PendingChatMessageDeletion>()

    override fun load(sessionId: String): PendingChatMessageDeletion? = values[sessionId]

    override fun loadAll(): List<PendingChatMessageDeletion> = values.values.toList()

    override fun save(pending: PendingChatMessageDeletion) {
        values[pending.sessionId] = pending
    }

    override fun clear(sessionId: String) {
        values.remove(sessionId)
    }
}

private class CrashBeforeFirstClearPendingDeletionStore : ChatPendingDeletionStore {
    private val delegate = RecordingPendingDeletionStore()
    private var crashOnClear = true

    override fun load(sessionId: String): PendingChatMessageDeletion? = delegate.load(sessionId)

    override fun loadAll(): List<PendingChatMessageDeletion> = delegate.loadAll()

    override fun save(pending: PendingChatMessageDeletion) = delegate.save(pending)

    override fun clear(sessionId: String) {
        if (crashOnClear) {
            crashOnClear = false
            error("process died before pending clear")
        }
        delegate.clear(sessionId)
    }
}
