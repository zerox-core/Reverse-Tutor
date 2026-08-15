package com.reversetutor.core.model

data class MemoryItem(
    val id: String,
    val spaceId: String,
    val kind: MemoryItemKind,
    val title: String,
    val body: String,
    val createdAtEpochMillis: Long,
    val sourceMessageId: String? = null,
    val sourceId: String? = null
)

enum class MemoryItemKind {
    Requirement,
    Note,
    Error,
    Summary,
    Fact
}

data class Anchor(
    val id: String,
    val spaceId: String,
    val title: String,
    val body: String,
    val createdAtEpochMillis: Long,
    val sourceMessageId: String? = null,
    val sourceId: String? = null
)

data class Note(
    val id: String,
    val spaceId: String,
    val title: String,
    val body: String,
    val createdAtEpochMillis: Long,
    val sourceMessageId: String? = null
)

data class ErrorLog(
    val id: String,
    val spaceId: String,
    val title: String,
    val detail: String,
    val createdAtEpochMillis: Long,
    val sourceMessageId: String? = null,
    val resolved: Boolean = false,
    val origin: ErrorLogOrigin = ErrorLogOrigin.Learning,
    val code: String? = null
)

enum class ErrorLogOrigin {
    Learning,
    Generation
}
