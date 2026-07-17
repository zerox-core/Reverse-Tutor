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
                summary = "${items.size} 份资料",
                emptyTitle = "还没有资料",
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
                chunkCountLabel = "${chunks.size} 个片段",
                snippets = chunks.take(2).map { it.text.toSnippet() },
                actionLabel = if (source.parserStatus == SourceParserStatus.Failed) "重试" else "重新解析"
            )
        }
    }
}

private fun SourceImportResult.toDetailLines(): List<String> =
    buildList {
        add("已导入：${source.title}")
        add("类型：${source.type.typeLabel}")
        add("状态：${source.parserStatus.statusLabel}")
        add("片段：${chunks.size}")
        warnings.forEach { add("警告：$it") }
        errors.forEach { add("错误：$it") }
    }

private val SourceType.typeLabel: String
    get() = when (this) {
        SourceType.JsonExport -> "JSON 导出"
        SourceType.Pdf -> "PDF"
        SourceType.Docx -> "DOCX"
        SourceType.Text -> "TXT"
        SourceType.Markdown -> "Markdown"
        SourceType.Html -> "HTML"
        SourceType.Pptx -> "PPTX"
        SourceType.Epub -> "EPUB"
        SourceType.Image -> "图片"
        SourceType.Other -> "其他"
    }

private val SourceParserStatus.statusLabel: String
    get() = when (this) {
        SourceParserStatus.FullyLocal -> "已解析"
        SourceParserStatus.PartiallyLocal -> "部分解析"
        SourceParserStatus.FutureAssisted -> "等待能力"
        SourceParserStatus.Unsupported -> "暂不支持"
        SourceParserStatus.Failed -> "失败"
    }

private val SourceParserStatus.statusDetail: String
    get() = when (this) {
        SourceParserStatus.FullyLocal -> "已在本地解析，可用于片段和上下文引用。"
        SourceParserStatus.PartiallyLocal -> "已完成部分本地提取，使用前请查看警告。"
        SourceParserStatus.FutureAssisted -> "资料会继续保留并可恢复，等待辅助解析或视觉能力。"
        SourceParserStatus.Unsupported -> "当前版本暂不能处理这种文件，但资料会继续保留为参考。"
        SourceParserStatus.Failed -> "资料会继续保留。解析已尝试但失败，检查文件后可重试。"
    }

private fun String.toSnippet(): String {
    val compact = trim().replace(Regex("\\s+"), " ")
    return compact.take(180).ifEmpty { "空片段" }
}
