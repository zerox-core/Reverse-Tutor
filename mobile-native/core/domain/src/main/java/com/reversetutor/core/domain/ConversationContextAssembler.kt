package com.reversetutor.core.domain

/**
 * Bounded, deterministic conversation context assembler.
 *
 * Reads from non-frozen source ports, applying:
 * - spaceId/sessionId isolation (ports receive both, never cross sessions);
 * - per-category list limits;
 * - text length caps;
 * - stable deterministic sorting (by timestamp descending, then id);
 * - partial-failure degradation: a single source failure returns an empty
 *   category and a safe [ContextWarning], without blocking other sources.
 *
 * This assembler does not call LLM, Repository internals, or Android APIs.
 */
class ConversationContextAssembler(
    private val messagePort: MessageContextPort,
    private val memoryPort: MemoryContextPort,
    private val errorPort: ErrorContextPort,
    private val graphPort: GraphContextPort,
    private val sourcePort: SourceContextPort,
    private val messageLimit: Int = 10,
    private val memoryLimit: Int = 5,
    private val errorLimit: Int = 5,
    private val gapLimit: Int = 5,
    private val sourceLimit: Int = 5,
    private val reviewLimit: Int = 5,
    private val textCap: Int = 200
) {

    suspend fun assemble(spaceId: String, sessionId: String): ConversationContextContract {
        val warnings = mutableListOf<ContextWarning>()

        // Each source is read independently; failure degrades only that category.
        val messages = safeRead("message") {
            messagePort.listRecentMessages(spaceId, sessionId, messageLimit)
                .sortedWith(compareByDescending<ContextMessage> { it.timestampEpochMillis }
                    .thenBy { it.messageId })
                .map { it.copy(text = it.text.take(textCap)) }
        }.also { if (it.isEmpty()) warnings.add(ContextWarning("message", "source unavailable or empty")) }

        val memory = safeRead("memory") {
            memoryPort.listMemoryReferences(spaceId, sessionId, memoryLimit)
                .sortedWith(compareByDescending<MemoryReferenceContract> { it.updatedAtEpochMillis }
                    .thenBy { it.id })
                .map { it.copy(summary = it.summary.take(textCap)) }
        }.also { if (it.isEmpty()) warnings.add(ContextWarning("memory", "source unavailable or empty")) }

        val errors = safeRead("error") {
            errorPort.listHistoricalErrors(spaceId, sessionId, errorLimit)
                .sortedWith(compareByDescending<ErrorReferenceContract> { it.timestampEpochMillis }
                    .thenBy { it.id })
                .map { it.copy(description = it.description.take(textCap)) }
        }.also { if (it.isEmpty()) warnings.add(ContextWarning("error", "source unavailable or empty")) }

        val gaps = safeRead("graph_gaps") {
            graphPort.listPrerequisiteGaps(spaceId, sessionId, gapLimit)
                .map { it.take(textCap) }
        }.also { if (it.isEmpty()) warnings.add(ContextWarning("graph_gaps", "source unavailable or empty")) }

        val reviewPoints = safeRead("graph_review") {
            graphPort.listPendingReviewPoints(spaceId, sessionId, reviewLimit)
                .map { it.take(textCap) }
        }.also { if (it.isEmpty()) warnings.add(ContextWarning("graph_review", "source unavailable or empty")) }

        val sources = safeRead("source") {
            sourcePort.listSourceEvidence(spaceId, sessionId, sourceLimit)
                .sortedWith(compareByDescending<SourceReferenceContract> { it.relevanceScore }
                    .thenBy { it.id })
                .map {
                    it.copy(
                        title = it.title.take(textCap),
                        excerpt = it.excerpt.take(textCap)
                    )
                }
        }.also { if (it.isEmpty()) warnings.add(ContextWarning("source", "source unavailable or empty")) }

        return ConversationContextContract(
            spaceId = spaceId,
            sessionId = sessionId,
            prerequisiteGaps = gaps,
            relatedMemory = memory,
            sourceEvidence = sources,
            historicalErrors = errors,
            pendingReviewKnowledgePoints = reviewPoints,
            recentMessages = messages,
            warnings = warnings
        )
    }

    /**
     * Calls [block] and returns its result, or an empty list + warning on failure.
     * Only the failing category is affected; other categories proceed.
     */
    private suspend fun <T> safeRead(
        source: String,
        block: suspend () -> List<T>
    ): List<T> = try {
        block()
    } catch (e: Exception) {
        emptyList()
    }
}
