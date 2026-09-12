package com.reversetutor.preview.shell

import android.content.Context
import android.net.Uri

/**
 * 电子书资料导入的 Android 侧包装（NEWMP-V1-023）。
 *
 * 解析逻辑在 EpubSourceText.kt（纯 JVM、可单测）；这里只负责从 content://
 * 打开文件喂给解析器。嵌入图片转写直接复用 DocxSourceImport.kt 的
 * transcribeDocxEmbeddedImage（本地 OCR 优先、云端多模态兜底）。
 */
internal suspend fun extractEpubSourceText(
    context: Context,
    uri: Uri,
    transcribeImage: suspend (DocxEmbeddedImage) -> String?
): String? {
    return try {
        val stream = context.contentResolver.openInputStream(uri) ?: return null
        stream.use { input -> extractEpubSourceText(input, transcribeImage) }
    } catch (t: Throwable) {
        null
    }
}
