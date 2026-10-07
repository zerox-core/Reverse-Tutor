package com.reversetutor.core.model

data class BackgroundJob(
    val id: String,
    val spaceId: String,
    val kind: BackgroundJobKind,
    val status: BackgroundJobStatus,
    val createdAtEpochMillis: Long,
    val sessionId: String? = null,
    val completedAtEpochMillis: Long? = null,
    val errorMessage: String? = null
)

enum class BackgroundJobKind {
    Generation,
    SourceParsing,
    Import,
    Export
}

enum class BackgroundJobStatus {
    Queued,
    Running,
    Completed,
    Failed,
    Cancelled,
    Discarded
}

data class ImportBatch(
    val id: String,
    val spaceId: String,
    val sourceFileName: String,
    val sourceSchema: String,
    val mode: ImportMode,
    val status: ImportStatus,
    val startedAtEpochMillis: Long,
    val completedAtEpochMillis: Long? = null,
    val insertedCountsJson: String = "{}",
    val skippedCountsJson: String = "{}",
    val warningsJson: String = "[]",
    val errorsJson: String = "[]"
)

enum class ImportMode {
    Append,
    Overwrite,
    NewSpace
}

enum class ImportStatus {
    Pending,
    Running,
    Completed,
    Failed,
    Cancelled
}

data class ExportRecord(
    val id: String,
    val spaceId: String,
    val schema: String,
    val targetFileName: String,
    val status: ExportStatus,
    val createdAtEpochMillis: Long,
    val completedAtEpochMillis: Long? = null,
    val warningsJson: String = "[]"
)

enum class ExportStatus {
    Created,
    Completed,
    Failed
}
