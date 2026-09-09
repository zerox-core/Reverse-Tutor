package com.reversetutor.feature.chat

import com.reversetutor.core.data.background.BackgroundGenerationJob
import com.reversetutor.core.llm.LlmGenerationToken
import com.reversetutor.core.model.BackgroundJobStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * NEWMP-V1-006 Task 5: the home session card must reflect the same persisted
 * background-job truth the chat page observes — never chat text.
 */
class SessionCardGenerationTest {
    @Test
    fun queuedAndRunningJobsMarkTheSessionCardAsGenerating() {
        val card = sessionCard()

        assertEquals(
            "生成中",
            card.withActiveGenerationJob(job(BackgroundJobStatus.Queued)).statusLabel
        )
        assertEquals(
            "生成中",
            card.withActiveGenerationJob(job(BackgroundJobStatus.Running)).statusLabel
        )
    }

    @Test
    fun completedMissingOrCancelledJobsKeepTheLatestMessageSummary() {
        val card = sessionCard()

        assertEquals(
            "最新消息：勾股定理",
            card.withActiveGenerationJob(job(BackgroundJobStatus.Completed)).statusLabel
        )
        assertEquals("最新消息：勾股定理", card.withActiveGenerationJob(null).statusLabel)
        assertEquals(
            "最新消息：勾股定理",
            card.withActiveGenerationJob(job(BackgroundJobStatus.Cancelled)).statusLabel
        )
    }

    @Test
    fun failedJobShowsSafeFailureLabelWithoutPersistedErrorDetails() {
        val persistedError = "java.lang.RuntimeException at https://secret.example?key=sk-abc"

        val label = sessionCard().withActiveGenerationJob(
            job(BackgroundJobStatus.Failed, errorMessage = persistedError)
        ).statusLabel

        assertEquals("生成失败：后台生成失败", label)
    }

    private fun sessionCard() = SessionListItem(
        id = "session-1",
        title = "会话一",
        updatedAtEpochMillis = 1L,
        pinned = false,
        statusLabel = "最新消息：勾股定理",
        unreadCount = 0,
        avatarLabel = "会"
    )

    private fun job(
        status: BackgroundJobStatus,
        errorMessage: String? = null
    ): BackgroundGenerationJob = BackgroundGenerationJob(
        id = "job-1",
        spaceId = "space-1",
        sessionId = "session-1",
        userMessageId = null,
        userText = null,
        token = LlmGenerationToken("token-1"),
        status = status,
        createdAtEpochMillis = 1L,
        errorMessage = errorMessage
    )
}
