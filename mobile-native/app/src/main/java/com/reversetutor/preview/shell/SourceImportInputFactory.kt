package com.reversetutor.preview.shell

import com.reversetutor.core.data.sources.SourceImportInput
import java.io.Reader

internal const val MaxSourceTextChars = 500_000

internal suspend fun buildSourceImportInput(
    requestId: Long,
    fileName: String?,
    mimeType: String?,
    uri: String,
    readText: suspend () -> String?
): SourceImportInput {
    val safeFileName = fileName?.takeIf { it.isNotBlank() } ?: "selected-source"
    val text = if (canUseLocalTextParser(safeFileName, mimeType)) {
        try {
            readText()
        } catch (t: Throwable) {
            null
        }
    } else {
        null
    }
    return SourceImportInput(
        requestId = requestId,
        fileName = safeFileName,
        mimeType = mimeType,
        uri = uri,
        text = text
    )
}

private fun canUseLocalTextParser(
    fileName: String,
    mimeType: String?
): Boolean {
    val ext = fileName.lowercase().substringAfterLast('.', missingDelimiterValue = "")
    val normalizedMime = mimeType.orEmpty().lowercase()
    return ext in setOf(
        "txt",
        "md",
        "markdown",
        "html",
        "htm",
        "pdf",
        "png",
        "jpg",
        "jpeg",
        "webp",
        "gif",
        "bmp"
    ) ||
        normalizedMime in setOf(
            "text/plain",
            "text/markdown",
            "text/x-markdown",
            "text/html",
            "application/pdf"
        ) ||
        normalizedMime.startsWith("image/")
}

internal fun readSourceTextWithinLimit(
    reader: Reader,
    maxChars: Int = MaxSourceTextChars
): String? {
    require(maxChars > 0) { "maxChars must be positive" }
    val buffer = CharArray(4096)
    val builder = StringBuilder()
    while (true) {
        val read = reader.read(buffer)
        if (read == -1) {
            return builder.toString()
        }
        if (builder.length + read > maxChars) {
            return null
        }
        builder.append(buffer, 0, read)
    }
}
