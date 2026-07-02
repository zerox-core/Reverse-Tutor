package com.reversetutor.core.model

data class Space(
    val id: String,
    val name: String,
    val kind: SpaceKind,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val sourceImportId: String? = null
)

enum class SpaceKind {
    Default,
    Imported,
    Archive
}
