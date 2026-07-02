package com.reversetutor.feature.sources

import com.reversetutor.core.data.sources.SourceImportResult
import com.reversetutor.core.data.sources.SourceWithChunks
import com.reversetutor.core.model.SourceParserStatus
import com.reversetutor.core.model.SourceType

data class SourcesUiState(
    val summary: String,
    val emptyTitle: String,
    val importStatusLabel: String?,
    val importDetailLines: List<String>,
    val items: List<SourceCardUiItem>
) {
    val isEmpty: Boolean
        get() = items.isEmpty()

    companion object {
        fun from(
            sources: List<SourceWithChunks>,
            lastImport: SourceImportResult?
        ): SourcesUiState {
            val items = sources.map { SourceCardUiItem.from(it) }
            return SourcesUiState(
                summary = "${items.size} ${if (items.size == 1) "source" else "sources"}",
                emptyTitle = "No sources yet",
                importStatusLabel = lastImport?.source?.parserStatus?.statusLabel,
                importDetailLines = lastImport?.toDetailLines().orEmpty(),
                items = items
            )
        }
    }
}

data class SourceCardUiItem(
    val id: String,
    val title: String,
    val typeLabel: String,
    val statusLabel: String,
    val statusDetail: String,
    val chunkCountLabel: String,
    val snippets: List<String>,
    val actionLabel: String
) {
    companion object {
        fun from(sourceWithChunks: SourceWithChunks): SourceCardUiItem {
            val source = sourceWithChunks.source
            val chunks = sourceWithChunks.chunks
            return SourceCardUiItem(
                id = source.id,
                title = source.title,
                typeLabel = source.type.typeLabel,
                statusLabel = source.parserStatus.statusLabel,
                statusDetail = source.parserStatus.statusDetail,
                chunkCountLabel = "${chunks.size} ${if (chunks.size == 1) "chunk" else "chunks"}",
                snippets = chunks.take(2).map { it.text.toSnippet() },
                actionLabel = if (source.parserStatus == SourceParserStatus.Failed) "Retry" else "Reprocess"
            )
        }
    }
}

private fun SourceImportResult.toDetailLines(): List<String> =
    buildList {
        add("Imported: ${source.title}")
        add("Type: ${source.type.typeLabel}")
        add("Status: ${source.parserStatus.statusLabel}")
        add("Chunks: ${chunks.size}")
        warnings.forEach { add("Warning: $it") }
        errors.forEach { add("Error: $it") }
    }

private val SourceType.typeLabel: String
    get() = when (this) {
        SourceType.JsonExport -> "JSON export"
        SourceType.Pdf -> "PDF"
        SourceType.Docx -> "DOCX"
        SourceType.Text -> "TXT"
        SourceType.Markdown -> "Markdown"
        SourceType.Html -> "HTML"
        SourceType.Pptx -> "PPTX"
        SourceType.Epub -> "EPUB"
        SourceType.Image -> "Image"
        SourceType.Other -> "Other"
    }

private val SourceParserStatus.statusLabel: String
    get() = when (this) {
        SourceParserStatus.FullyLocal -> "supported_local"
        SourceParserStatus.PartiallyLocal -> "partial_local"
        SourceParserStatus.FutureAssisted -> "queued_for_future_api"
        SourceParserStatus.Unsupported -> "unsupported"
        SourceParserStatus.Failed -> "failed"
    }

private val SourceParserStatus.statusDetail: String
    get() = when (this) {
        SourceParserStatus.FullyLocal -> "Parsed locally and ready for snippets."
        SourceParserStatus.PartiallyLocal -> "Partial local extraction is available; review warnings before relying on it."
        SourceParserStatus.FutureAssisted -> "The file stays visible as source material while waiting for the assisted parser or vision path."
        SourceParserStatus.Unsupported -> "The current build cannot process this file type."
        SourceParserStatus.Failed -> "Parsing was attempted and failed; retry after checking the file."
    }

private fun String.toSnippet(): String {
    val compact = trim().replace(Regex("\\s+"), " ")
    return compact.take(180).ifEmpty { "Empty chunk" }
}
