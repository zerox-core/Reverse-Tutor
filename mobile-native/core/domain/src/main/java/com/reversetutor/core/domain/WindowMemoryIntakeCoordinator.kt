package com.reversetutor.core.domain

/**
 * V2-004: window-memory intake pipeline (domain orchestration).
 *
 * Locked decision #9 (docs/NEWMP-V2-memory-decisions.md): extraction runs
 * when messages slide OUT of the sliding window, in batches, off the chat
 * loop — never per sentence. The coordinator is pure domain logic; adapters
 * supply messages and persistence through the ports below.
 *
 * Flow per completed turn:
 *   1. list messages -> build sliding-window views -> evict (layer 0)
 *   2. fold the newly evicted batch into the rolling summary (if enabled)
 *   3. rule-extract observations from the newly evicted batch (layer 1)
 *   4. evolve window-level active values via WindowActiveValuePolicy (layer 3)
 *   5. advance the watermark so each batch is processed exactly once
 *
 * Patterns (layer 2) stay read-time aggregates over stored observations via
 * [WindowMemoryPolicy.aggregatePatterns]; no extra storage.
 */

/** Minimal message view the intake pipeline reasons about. */
data class WindowIntakeMessage(
    val messageId: String,
    val role: ExtractionRole,
    val text: String,
    val occurredAtEpochMillis: Long,
)

/** Adapter port: messages of one session, oldest first. */
interface WindowIntakeMessagePort {
    suspend fun listMessages(sessionId: String): List<WindowIntakeMessage>
}

/** Last evicted batch marker: everything up to this message was processed. */
data class WindowIntakeWatermark(
    val lastProcessedMessageId: String,
    val lastProcessedEpochMillis: Long,
)

/** Adapter port: window-memory persistence (observations / values / summary). */
interface WindowIntakeStore {
    suspend fun loadWatermark(sessionId: String): WindowIntakeWatermark?
    suspend fun saveWatermark(sessionId: String, watermark: WindowIntakeWatermark)
    suspend fun appendObservations(sessionId: String, observations: List<WindowLocalObservation>)
    suspend fun loadActiveValue(
        sessionId: String,
        category: WindowObservationCategory,
        slotKey: String,
    ): WindowActiveValue?
    suspend fun saveActiveValue(sessionId: String, value: WindowActiveValue)
    suspend fun loadRollingSummary(sessionId: String): String?
    suspend fun saveRollingSummary(
        sessionId: String,
        summary: String,
        coversUntilMessageId: String,
        nowEpochMillis: Long,
    )
}

/** Per-run accounting so the wiring layer can meter without re-deriving. */
data class WindowIntakeReport(
    val windowKeptCount: Int,
    val evictedCount: Int,
    val newlyProcessedCount: Int,
    val observationsExtracted: Int,
    val createdCount: Int,
    val reinforcedCount: Int,
    val supersededCount: Int,
    val conflictCount: Int,
    val ignoredCount: Int,
    val summaryFolded: Boolean,
)

/**
 * Heuristic token estimate for sliding-window budgeting. CJK characters
 * count as one token each; other characters average four per token. This is
 * a budgeting heuristic only — V2-006 adds per-turn metering so the default
 * budget can be tuned against real usage.
 */
object WindowTokenEstimator {
    fun estimate(text: String): Int {
        if (text.isEmpty()) return 1
        var cjk = 0
        var other = 0
        for (ch in text) {
            val code = ch.code
            if (code in 0x2E80..0x9FFF || code in 0x3000..0x303F || code in 0xFF00..0xFFEF) {
                cjk++
            } else {
                other++
            }
        }
        return maxOf(1, cjk + (other + 3) / 4)
    }
}

class WindowMemoryIntakeCoordinator(
    private val messagePort: WindowIntakeMessagePort,
    private val store: WindowIntakeStore,
    private val config: WindowMemoryConfig = WindowMemoryConfig(),
    private val hourOfDayAt: (Long) -> Int,
    private val estimateTokens: (String) -> Int = WindowTokenEstimator::estimate,
    private val foldSummary: (suspend (sessionId: String, currentSummary: String?, evicted: List<WindowIntakeMessage>) -> String?)? = null,
) {

    suspend fun onTurnCompleted(sessionId: String, nowEpochMillis: Long): WindowIntakeReport {
        val messages = messagePort.listMessages(sessionId)
        if (messages.isEmpty()) {
            return WindowIntakeReport(0, 0, 0, 0, 0, 0, 0, 0, 0, summaryFolded = false)
        }
        val views = messages.map {
            WindowMessageView(
                messageId = it.messageId,
                estimatedTokens = estimateTokens(it.text),
                occurredAtEpochMillis = it.occurredAtEpochMillis,
            )
        }
        val eviction = WindowMemoryPolicy.evict(views, config)
        if (eviction.evicted.isEmpty()) {
            return WindowIntakeReport(
                windowKeptCount = eviction.kept.size,
                evictedCount = 0,
                newlyProcessedCount = 0,
                observationsExtracted = 0,
                createdCount = 0,
                reinforcedCount = 0,
                supersededCount = 0,
                conflictCount = 0,
                ignoredCount = 0,
                summaryFolded = false,
            )
        }

        val watermark = store.loadWatermark(sessionId)
        val evictedIds = eviction.evicted.map { it.messageId }
        val newlyEvicted = when {
            watermark == null -> eviction.evicted
            else -> {
                val markIndex = evictedIds.indexOf(watermark.lastProcessedMessageId)
                if (markIndex >= 0) {
                    eviction.evicted.subList(markIndex + 1, eviction.evicted.size)
                } else {
                    eviction.evicted.filter { it.occurredAtEpochMillis > watermark.lastProcessedEpochMillis }
                }
            }
        }

        var summaryFolded = false
        var observations: List<WindowLocalObservation> = emptyList()
        var created = 0
        var reinforced = 0
        var superseded = 0
        var conflict = 0
        var ignored = 0

        if (newlyEvicted.isNotEmpty()) {
            val evictedMessagesById = messages.associateBy { it.messageId }
            val batch = newlyEvicted.mapNotNull { evictedMessagesById[it.messageId] }

            val folder = foldSummary
            if (config.rollingSummaryEnabled && folder != null) {
                val folded = folder.invoke(sessionId, store.loadRollingSummary(sessionId), batch)
                if (folded != null) {
                    store.saveRollingSummary(
                        sessionId = sessionId,
                        summary = folded,
                        coversUntilMessageId = newlyEvicted.last().messageId,
                        nowEpochMillis = nowEpochMillis,
                    )
                    summaryFolded = true
                }
            }

            observations = RuleBasedMemoryExtractor.extract(
                batch.map {
                    ExtractableMessage(
                        messageId = it.messageId,
                        role = it.role,
                        text = it.text,
                        occurredAtEpochMillis = it.occurredAtEpochMillis,
                        hourOfDay = hourOfDayAt(it.occurredAtEpochMillis),
                    )
                },
            )
            if (observations.isNotEmpty()) {
                store.appendObservations(sessionId, observations)
                for (observation in observations) {
                    val current = store.loadActiveValue(sessionId, observation.category, observation.slotKey)
                    when (WindowActiveValuePolicy.decide(current, observation)) {
                        WindowEvolutionDecision.CREATE -> {
                            store.saveActiveValue(
                                sessionId,
                                WindowActiveValuePolicy.supersede(null, observation),
                            )
                            created++
                        }
                        WindowEvolutionDecision.REINFORCE -> {
                            store.saveActiveValue(
                                sessionId,
                                WindowActiveValuePolicy.reinforce(requireNotNull(current), observation),
                            )
                            reinforced++
                        }
                        WindowEvolutionDecision.SUPERSEDE -> {
                            store.saveActiveValue(
                                sessionId,
                                WindowActiveValuePolicy.supersede(current, observation),
                            )
                            superseded++
                        }
                        WindowEvolutionDecision.CONFLICT -> conflict++
                        WindowEvolutionDecision.IGNORE -> ignored++
                    }
                }
            }

            val newest = newlyEvicted.last()
            store.saveWatermark(
                sessionId,
                WindowIntakeWatermark(
                    lastProcessedMessageId = newest.messageId,
                    lastProcessedEpochMillis = newest.occurredAtEpochMillis,
                ),
            )
        }

        return WindowIntakeReport(
            windowKeptCount = eviction.kept.size,
            evictedCount = eviction.evicted.size,
            newlyProcessedCount = newlyEvicted.size,
            observationsExtracted = observations.size,
            createdCount = created,
            reinforcedCount = reinforced,
            supersededCount = superseded,
            conflictCount = conflict,
            ignoredCount = ignored,
            summaryFolded = summaryFolded,
        )
    }
}
