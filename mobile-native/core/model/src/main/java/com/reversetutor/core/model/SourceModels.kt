package com.reversetutor.core.model

data class SourceRecord(
    val id: String,
    val spaceId: String,
    val title: String,
    val type: SourceType,
    val parserStatus: SourceParserStatus,
    val createdAtEpochMillis: Long,
    val uri: String? = null,
    val extractedText: String? = null,
    val importBatchId: String? = null
)

enum class SourceType {
    JsonExport,
    Pdf,
    Docx,
    Text,
    Markdown,
    Html,
    Pptx,
    Epub,
    Image,
    Other
}

enum class SourceParserStatus {
    FullyLocal,
    PartiallyLocal,
    FutureAssisted,
    Unsupported,
    Failed
}

data class SourceChunk(
    val id: String,
    val spaceId: String,
    val sourceId: String,
    val chunkIndex: Int,
    val text: String,
    val tokenEstimate: Int? = null
)
