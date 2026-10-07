package com.reversetutor.preview.wiring.session

import com.reversetutor.core.data.llm.SessionSummaryOutcome
import com.reversetutor.core.model.Message
import com.reversetutor.core.model.MessageRole
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * NEWMP-V1-017 tests for [SessionSummarizer]: threshold semantics, cutoff
 * tracking, previous-summary merging, failure tolerance, and role filtering.
 */
class SessionSummarizerTest {

    private class FakeStore : SessionSummaryStore {
        val records = mutableMapOf<String, SessionSummaryRecord>()
        var saveCalls = 0

        override fun load(sessionId: String): SessionSummaryRecord? = records[sessionId]

        override fun save(sessionId: String, record: SessionSummaryRecord) {
            saveCalls += 1
            records[sessionId] = record
        }

        override fun clear(sessionId: String) {
            records.remove(sessionId)
        }

        override suspend fun loadEarlyHistoryDigest(spaceId: String, sessionId: String): String =
            load(sessionId)?.summaryText.orEmpty()
    }

    private class FakeSummaryEngine(
        var outcome: SessionSummaryOutcome = SessionSummaryOutcome.Generated("- 要点一")
    ) {
        val prompts = mutableListOf<Pair<String, String>>()
        val generate: suspend (String, String) -> SessionSummaryOutcome = { sessionId, prompt ->
            prompts += sessionId to prompt
            outcome
        }
    }

    private fun message(id: String, role: MessageRole, text: String = "内容" + id): Message = Message(
        id = id,
        spaceId = "space-1",
        sessionId = "session-1",
        role = role,
        text = text,
        createdAtEpochMillis = id.hashCode().toLong()
    )

    private fun summarizer(
        store: FakeStore,
        engine: FakeSummaryEngine,
        messages: List<Message>
    ): SessionSummarizer = SessionSummarizer(
        generateSummary = engine.generate,
        listMessages = { messages },
        store = store
    )

    @Test
    fun below_threshold_does_nothing() = runBlocking {
        val store = FakeStore()
        val engine = FakeSummaryEngine()
        // 29 chat messages: below the legacy threshold of 30.
        val messages = (1..14).flatMap {
            listOf(message("u$it", MessageRole.User), message("a$it", MessageRole.Assistant))
        } + message("u15", MessageRole.User)

        assertFalse(summarizer(store, engine, messages).maybeSummarize("session-1"))
        assertTrue(engine.prompts.isEmpty())
        assertNull(store.records["session-1"])
    }

    @Test
    fun at_threshold_compresses_all_but_recent_twelve() = runBlocking {
        val store = FakeStore()
        val engine = FakeSummaryEngine()
        val messages = (1..30).map {
            if (it % 2 == 1) message("m$it", MessageRole.User) else message("m$it", MessageRole.Assistant)
        }

        assertTrue(summarizer(store, engine, messages).maybeSummarize("session-1"))
        val record = requireNotNull(store.records["session-1"])
        assertEquals("m18", record.summarizedUntilMessageId)
        assertEquals(18, record.summarizedCount)

        // Only 12 unsummarized messages remain: the next trigger is a no-op.
        assertFalse(summarizer(store, engine, messages).maybeSummarize("session-1"))
        assertEquals(1, store.saveCalls)
    }

    @Test
    fun previous_summary_is_merged_into_prompt() = runBlocking {
        val store = FakeStore()
        store.records["session-1"] = SessionSummaryRecord("旧摘要要点", "m18", 18)
        val engine = FakeSummaryEngine()
        val messages = (1..48).map {
            if (it % 2 == 1) message("m$it", MessageRole.User) else message("m$it", MessageRole.Assistant)
        }

        assertTrue(summarizer(store, engine, messages).maybeSummarize("session-1"))
        val prompt = engine.prompts.single().second
        assertTrue(prompt.contains("此前已有摘要"))
        assertTrue(prompt.contains("旧摘要要点"))
        assertTrue(prompt.contains("新增对话"))
        val record = requireNotNull(store.records["session-1"])
        assertEquals("m36", record.summarizedUntilMessageId)
        assertEquals(18, record.summarizedCount)
    }

    @Test
    fun missing_cutoff_message_falls_back_to_whole_history() = runBlocking {
        val store = FakeStore()
        store.records["session-1"] = SessionSummaryRecord("旧摘要要点", "deleted-id", 18)
        val engine = FakeSummaryEngine()
        val messages = (1..31).map {
            if (it % 2 == 1) message("m$it", MessageRole.User) else message("m$it", MessageRole.Assistant)
        }

        assertTrue(summarizer(store, engine, messages).maybeSummarize("session-1"))
        val record = requireNotNull(store.records["session-1"])
        assertEquals("m19", record.summarizedUntilMessageId)
        assertEquals(19, record.summarizedCount)
        // The old digest is still merged so its points are not lost.
        assertTrue(engine.prompts.single().second.contains("旧摘要要点"))
    }

    @Test
    fun provider_failure_keeps_old_record_and_retries_next_turn() = runBlocking {
        val store = FakeStore()
        val oldRecord = SessionSummaryRecord("旧摘要要点", "m18", 18)
        store.records["session-1"] = oldRecord
        val engine = FakeSummaryEngine(outcome = SessionSummaryOutcome.ProviderFailed("llm_provider_request_failed"))
        val messages = (1..48).map {
            if (it % 2 == 1) message("m$it", MessageRole.User) else message("m$it", MessageRole.Assistant)
        }

        assertFalse(summarizer(store, engine, messages).maybeSummarize("session-1"))
        assertEquals(oldRecord, store.records["session-1"])
        assertEquals(0, store.saveCalls)
    }

    @Test
    fun list_failure_never_throws() = runBlocking {
        val store = FakeStore()
        val engine = FakeSummaryEngine()
        val failing = SessionSummarizer(
            generateSummary = engine.generate,
            listMessages = { throw RuntimeException("db down") },
            store = store
        )

        assertFalse(failing.maybeSummarize("session-1"))
        assertTrue(engine.prompts.isEmpty())
    }

    @Test
    fun non_chat_roles_do_not_count_toward_the_threshold() = runBlocking {
        val store = FakeStore()
        val engine = FakeSummaryEngine()
        val chat = (1..29).map {
            if (it % 2 == 1) message("m$it", MessageRole.User) else message("m$it", MessageRole.Assistant)
        }
        val noise = (1..30).map { message("s$it", MessageRole.System, "系统消息") }

        assertFalse(summarizer(store, engine, chat + noise).maybeSummarize("session-1"))
        assertTrue(engine.prompts.isEmpty())
    }

    @Test
    fun generated_summary_is_capped_to_max_chars() = runBlocking {
        val store = FakeStore()
        val engine = FakeSummaryEngine(
            outcome = SessionSummaryOutcome.Generated("长".repeat(SessionSummarizer.MaxSummaryChars + 1_000))
        )
        val messages = (1..30).map {
            if (it % 2 == 1) message("m$it", MessageRole.User) else message("m$it", MessageRole.Assistant)
        }

        assertTrue(summarizer(store, engine, messages).maybeSummarize("session-1"))
        assertEquals(
            SessionSummarizer.MaxSummaryChars,
            requireNotNull(store.records["session-1"]).summaryText.length
        )
    }
}
