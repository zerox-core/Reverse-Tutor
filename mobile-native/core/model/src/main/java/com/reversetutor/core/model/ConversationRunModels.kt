package com.reversetutor.core.model

data class TurnRun(
    val id: String,
    val spaceId: String,
    val turnId: String,
    val sessionId: String,
    val userMessageId: String,
    val sequence: Long,
    val contextVersion: Long,
    val modelBindingId: String,
    val parentTurnId: String? = null,
    val contextSnapshotId: String? = null,
    val attempt: Int = 0,
    val state: TurnRunState = TurnRunState.Waiting,
    val createdAtEpochMillis: Long = 0L,
    val startedAtEpochMillis: Long? = null,
    val completedAtEpochMillis: Long? = null,
    val resultMessageId: String? = null,
    val error: DomainError? = null
) {
    val isTerminal: Boolean
        get() = state.isTerminal
}

enum class TurnRunState {
    Waiting,
    Running,
    Completed,
    Failed,
    Cancelled,
    Discarded;

    val isTerminal: Boolean
        get() = this == Completed ||
            this == Failed ||
            this == Cancelled ||
            this == Discarded
}

data class ContextSnapshot(
    val id: String,
    val spaceId: String,
    val sessionId: String,
    val turnId: String,
    val version: Long,
    val messageIds: List<String> = emptyList(),
    val parentTurnId: String? = null,
    val maxSequence: Long = 0L,
    val createdAtEpochMillis: Long = 0L
)
