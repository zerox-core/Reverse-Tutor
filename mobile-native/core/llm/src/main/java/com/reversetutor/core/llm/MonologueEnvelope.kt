package com.reversetutor.core.llm

/**
 * Expression-loop slice 4 (SPEC §4.7 route C): the student persona emits a
 * short first-person monologue before the real reply, separated by plain
 * text markers so the SAME stream can be split while it is still arriving —
 * the body streams into the bubble, the monologue rides into the collapsed
 * drawer and the trajectory for troubleshooting.
 *
 * The monologue never passes the red-line validator (SPEC §10 decision 9):
 * only the spoken body is checked, previewed and persisted as message text.
 * Legacy replies without the leading marker are unchanged by design.
 */
object MonologueEnvelope {

    const val THINK_START = "<thinking>"
    const val THINK_END = "</thinking>"

    /** Drawer/DB bound: monologues are short by design, never essays. */
    const val MAX_MONOLOGUE_CHARACTERS = 600

    /** Trajectory payload bound for the serialized self-assessment. */
    const val MAX_SELF_ASSESSMENT_CHARACTERS = 480

    data class SplitResult(
        val monologue: String?,
        val body: String
    )

    /**
     * Splits a COMPLETE assistant reply. Without a leading marker the whole
     * text is the body (legacy / retry / fallback replies stay unchanged). An
     * unclosed marker keeps everything after it as the monologue and leaves
     * the body empty, so the caller's blank-body path surfaces the safe hint.
     */
    fun splitComplete(text: String): SplitResult {
        val trimmed = text.trim()
        if (!trimmed.startsWith(THINK_START)) return SplitResult(null, text)
        val end = trimmed.indexOf(THINK_END)
        if (end < 0) {
            val monologue = trimmed.removePrefix(THINK_START).trim()
            return SplitResult(monologue.takeIf { it.isNotEmpty() }, "")
        }
        val monologue = trimmed.substring(THINK_START.length, end).trim()
        val body = trimmed.substring(end + THINK_END.length).trim()
        return SplitResult(
            monologue.take(MAX_MONOLOGUE_CHARACTERS).takeIf { it.isNotEmpty() },
            body
        )
    }

    /** True while [buffer] could still grow into [THINK_START] (stream hold). */
    fun couldStillStartMonologue(buffer: String): Boolean =
        THINK_START.startsWith(buffer.trimStart())

    /**
     * Serializes the envelope's structured self-assessment (SPEC §4.4, reuses
     * the existing v1 outcome field — zero extra model calls) into the compact
     * payload persisted on the turn trajectory. Empty outcomes yield null so
     * the column stays clean on plain-prose turns.
     */
    fun selfAssessmentPayload(outcome: StructuredTurnOutcome): String? {
        if (outcome == StructuredTurnOutcome.EMPTY) return null
        return buildList {
            outcome.knowledgePoint.takeIf { it.isNotEmpty() }?.let { add("kp=$it") }
            add("evidence=${outcome.evidenceType}/${outcome.evidenceStatus}")
            add("correctness=${outcome.correctness}")
            add("depth=${outcome.depth}")
            outcome.processSummary.takeIf { it.isNotEmpty() }?.let { add("summary=$it") }
        }.joinToString("|").take(MAX_SELF_ASSESSMENT_CHARACTERS)
    }
}

/**
 * Streaming companion to [MonologueEnvelope.splitComplete]: feed it the raw
 * chunks in arrival order and it yields only the newly visible BODY deltas —
 * the monologue segment is held back so it never reaches the bubble preview.
 * Red-line checking therefore only ever sees the spoken body (decision 9).
 */
class MonologueStreamSplitter {

    private val buffer = StringBuilder()
    private var emittedBodyLength = 0

    /** Returns the newly visible body text, or null while nothing new is visible. */
    fun onChunk(chunk: String): String? {
        if (chunk.isEmpty()) return null
        buffer.append(chunk)
        val visibleBody = visibleBodyPrefix() ?: return null
        if (visibleBody.length <= emittedBodyLength) return null
        val delta = visibleBody.substring(emittedBodyLength)
        emittedBodyLength = visibleBody.length
        return delta
    }

    fun splitSoFar(): MonologueEnvelope.SplitResult =
        MonologueEnvelope.splitComplete(buffer.toString())

    private fun visibleBodyPrefix(): String? {
        val raw = buffer.toString()
        val trimmed = raw.trimStart()
        if (trimmed.startsWith(MonologueEnvelope.THINK_START)) {
            val end = trimmed.indexOf(MonologueEnvelope.THINK_END)
            if (end < 0) return null // monologue still streaming
            return trimmed.substring(end + MonologueEnvelope.THINK_END.length).trimStart()
        }
        // Hold only while the buffer could still grow into the start marker;
        // any other text is legacy / monologue-less and streams through.
        if (MonologueEnvelope.couldStillStartMonologue(raw)) return null
        return raw
    }
}
