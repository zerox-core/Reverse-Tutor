package com.reversetutor.core.data.llm

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.reversetutor.core.llm.LlmImagePayloadResolver
import com.reversetutor.core.llm.LlmResolvedImage
import com.reversetutor.core.model.MessageAttachment

/** Converts a local image to a provider-safe in-memory payload at execution time. */
class AndroidImagePayloadResolver(
    private val context: Context
) : LlmImagePayloadResolver {
    override suspend fun resolve(attachment: MessageAttachment): LlmResolvedImage? {
        val uriText = attachment.uri?.trim().orEmpty()
        val mimeType = attachment.mimeType?.takeIf { it.startsWith("image/") } ?: return null
        if (uriText.startsWith("data:$mimeType;base64,")) {
            return uriText.substringAfter(',', missingDelimiterValue = "")
                .takeIf { it.isNotBlank() }
                ?.let { LlmResolvedImage(mimeType, it) }
        }
        val uri = runCatching { Uri.parse(uriText) }.getOrNull() ?: return null
        // NEWMP-V1-021: app-private temp files (docx embedded images prepared
        // for vision transcription) arrive as file:// URIs.
        if (uri.scheme == "file") {
            val fileBytes = runCatching {
                java.io.File(uri.path ?: return null).inputStream().use { input ->
                    input.readBytes().takeIf { it.size in 1..MaxImageBytes }
                }
            }.getOrNull() ?: return null
            return LlmResolvedImage(
                mimeType = mimeType,
                base64Data = Base64.encodeToString(fileBytes, Base64.NO_WRAP)
            )
        }
        if (uri.scheme != "content") return null
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                input.readBytes().takeIf { it.size in 1..MaxImageBytes }
            }
        }.getOrNull() ?: return null
        return LlmResolvedImage(
            mimeType = mimeType,
            base64Data = Base64.encodeToString(bytes, Base64.NO_WRAP)
        )
    }

    private companion object {
        const val MaxImageBytes = 20 * 1024 * 1024
    }
}
