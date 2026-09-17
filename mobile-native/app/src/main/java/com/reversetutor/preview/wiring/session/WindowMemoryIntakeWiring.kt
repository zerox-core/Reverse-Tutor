package com.reversetutor.preview.wiring.session

import com.reversetutor.core.data.llm.SessionSummaryOutcome
import com.reversetutor.core.data.windowmemory.WindowMemoryRepository
import com.reversetutor.core.data.windowmemory.WindowTokenMeterRepository
import com.reversetutor.core.domain.CategorizedObservation
import com.reversetutor.core.domain.ExtractionRole
import com.reversetutor.core.domain.WindowActiveValue
import com.reversetutor.core.domain.WindowIntakeMessage
import com.reversetutor.core.domain.WindowIntakeMessagePort
import com.reversetutor.core.domain.WindowIntakeStore
import com.reversetutor.core.domain.WindowIntakeWatermark
import com.reversetutor.core.domain.WindowLocalObservation
import com.reversetutor.core.domain.WindowMemoryContextPort
import com.reversetutor.core.domain.WindowMemoryContextSelector
import com.reversetutor.core.domain.WindowMemoryPolicy
import com.reversetutor.core.domain.WindowObservationCategory
import com.reversetutor.core.model.Message
import com.reversetutor.core.model.MessageRole
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * V2-004: window-memory intake wiring.
 *
 * Locked decision #9 (docs/NEWMP-V2-memory-decisions.md): rule extraction
 * runs when messages slide OUT of the sliding window, in batches, off the
 * chat loop - never per sentence, never blocking a turn. This file binds the
 * domain intake coordinator to production repositories:
 *
 * ```
 * SessionConversationAssembly.runTurn
 *   -> WindowIntakeDispatcher (async, failure-swallowing)
 *        -> WindowMemoryIntakeCoordinator (core/domain)
 *             -> WindowIntakeMessagePortAdapter -> MessageRepository
 *             -> WindowIntakeStoreAdapter       -> WindowMemoryRepository
 *             -> windowIntakeFoldSummary        -> ChatGenerationRepository
 * ```
 */

/** Projects stored messages into the minimal intake view. */
class WindowIntakeMessagePortAdapter(
    private val listStoredMessages: suspend (sessionId: String) -> List<Message>,
) : WindowIntakeMessagePort {
    override suspend fun listMessages(sessionId: String): List<WindowIntakeMessage> =
        listStoredMessages(sessionId).map {
            WindowIntakeMessage(
                messageId = it.id,
                role = if (it.role == MessageRole.User) ExtractionRole.USER else ExtractionRole.ASSISTANT,
                text = it.text,
                occurredAtEpochMillis = it.createdAtEpochMillis,
            )
        }
}

/**
 * Intake persistence backed by [WindowMemoryRepository]. Entities and DAOs
 * never leave the data layer; only core/domain contracts cross this seam.
 */
class WindowIntakeStoreAdapter(
    private val repository: WindowMemoryRepository,
) : WindowIntakeStore {
    override suspend fun loadWatermark(sessionId: String): WindowIntakeWatermark? =
        repository.loadWatermark(sessionId)

    override suspend fun saveWatermark(sessionId: String, watermark: WindowIntakeWatermark) =
        repository.saveWatermark(sessionId, watermark)

    override suspend fun appendObservations(
        sessionId: String,
        observations: List<WindowLocalObservation>,
    ) = repository.appendObservations(sessionId, observations)

    override suspend fun loadActiveValue(
        sessionId: String,
        category: WindowObservationCategory,
        slotKey: String,
    ): WindowActiveValue? = repository.loadActiveValue(sessionId, category, slotKey)

    override suspend fun saveActiveValue(sessionId: String, value: WindowActiveValue) =
        repository.saveActiveValue(sessionId, value)

    override suspend fun loadRollingSummary(sessionId: String): String? =
        repository.loadRollingSummary(sessionId)?.summary

    override suspend fun saveRollingSummary(
        sessionId: String,
        summary: String,
        coversUntilMessageId: String,
        nowEpochMillis: Long,
    ) = repository.saveRollingSummary(sessionId, summary, coversUntilMessageId, nowEpochMillis)
}

/** Runnable handle for the intake pipeline; SAM so tests can substitute. */
fun interface WindowIntakeRunner {
    suspend fun onTurnCompleted(sessionId: String, nowEpochMillis: Long)
}

/**
 * Async trigger for the intake pipeline. Dispatch returns immediately; the
 * batch runs on [scope]. Any failure is swallowed - the watermark makes the
 * next turn retry the same batch, and a broken extractor must never break a
 * conversation turn (decision #9).
 */
class WindowIntakeDispatcher(
    private val runner: WindowIntakeRunner,
    private val scope: CoroutineScope,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) {
    fun dispatch(sessionId: String) {
        scope.launch {
            try {
                runner.onTurnCompleted(sessionId, nowEpochMillis())
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // Intake failures stay invisible to the chat loop by design.
            }
        }
    }
}

/**
 * Rolling-summary folder: merges the newly evicted batch into the stored
 * summary through the session-summary LLM path. Returns null on any failure
 * so the coordinator simply skips folding this batch (the batch is still
 * rule-extracted and the watermark advances).
 */
fun windowIntakeFoldSummary(
    generateSummary: suspend (sessionId: String, promptText: String) -> SessionSummaryOutcome,
): suspend (sessionId: String, currentSummary: String?, evicted: List<WindowIntakeMessage>) -> String? =
    { sessionId, currentSummary, evicted ->
        val transcript = evicted.joinToString("\n") {
            (if (it.role == ExtractionRole.USER) "学生" else "家教") + "：" + it.text
        }
        val prompt = "请把新滑出学习窗口的对话并入滚动摘要。" +
            "保留学习细节（学了什么、哪里卡住、目标与偏好），丢弃寒暄与客套。" +
            "只输出更新后的摘要文本，不要解释。\n" +
            "当前摘要：" + (currentSummary?.takeIf { it.isNotBlank() } ?: "（无）") + "\n" +
            "新滑出的对话：\n" + transcript
        when (val outcome = generateSummary(sessionId, prompt)) {
            is SessionSummaryOutcome.Generated -> outcome.summaryText
            else -> null
        }
    }

/**
 * V2-006: turns stored window memory into the bounded injection block.
 * Lorebook rule (locked): never inject everything - the domain selector
 * ranks (always-on > query relevance > weight > recency) and truncates by
 * the injection token budget. Every non-empty injection is metered so the
 * default budget can be tuned against real usage.
 */
class WindowMemoryContextPortAdapter(
    private val repository: WindowMemoryRepository,
    private val meterRepository: WindowTokenMeterRepository,
    private val hourOfDayAt: (Long) -> Int,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) : WindowMemoryContextPort {
    override suspend fun loadWindowMemoryContext(
        spaceId: String,
        sessionId: String,
        queryText: String,
    ): String {
        val activeValues = repository.listActiveValues(sessionId)
        val patterns = WindowMemoryPolicy.aggregatePatterns(
            repository.listObservations(sessionId).map {
                CategorizedObservation(
                    category = it.category,
                    occurredAtEpochMillis = it.occurredAtEpochMillis,
                    hourOfDay = hourOfDayAt(it.occurredAtEpochMillis),
                )
            },
        )
        val rollingSummary = repository.loadRollingSummary(sessionId)?.summary
        val block = WindowMemoryContextSelector.select(
            activeValues = activeValues,
            patterns = patterns,
            rollingSummary = rollingSummary,
            queryText = queryText,
        )
        if (block.text.isEmpty()) return ""
        meterRepository.recordTokenMeter(
            sessionId = sessionId,
            kind = "injection",
            estimatedTokens = block.estimatedTokens,
            detail = "values=" + block.includedValueCount +
                ",patterns=" + block.includedPatternCount +
                ",summary=" + block.summaryIncluded,
            createdAtEpochMillis = nowEpochMillis(),
        )
        return block.text
    }
}
