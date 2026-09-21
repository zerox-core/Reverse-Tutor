package com.reversetutor.core.data.llm

import com.reversetutor.core.domain.ReplyValidator

/**
 * Expression-loop slice 3 (SPEC §4.8 配套 / §9 切片 3): the per-turn
 * generation trajectory — what the note said, what the model actually
 * streamed, which checks fired, and whether a retry / fallback happened.
 *
 * Recorded once per completed generation via the recorder injected into
 * [ChatGenerationRepository]; persisted by the background layer into the
 * turn_run_trajectories table. The NEXT turn's style hint is derived from
 * [styleFlags] (see ReplyValidator.styleHintForPayload); the self-assessment
 * column arrives with the slice-4 envelope work.
 */
data class ReplyTrajectory(
    val sessionId: String,
    val userMessageId: String? = null,
    val generationToken: String,
    val turnNoteBlock: String? = null,
    val outputText: String,
    val abortedOutputText: String? = null,
    val redLines: List<ReplyValidator.RedLine> = emptyList(),
    val styleFlags: List<ReplyValidator.StyleFlag> = emptyList(),
    val retried: Boolean = false,
    val usedFallback: Boolean = false,
    /** Slice 4: compact serialized self-assessment from the envelope outcome (SPEC section 4.4). */
    val selfAssessment: String? = null,
    val modelId: String,
    val createdAtEpochMillis: Long
)
