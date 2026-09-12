package com.reversetutor.preview.shell

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.InputStream

/**
 * PDF 资料文本提取（V1-019 知识锚点第一期）。
 *
 * 只走 PDF 自带文本层；扫描件（无文本层）返回 null，交由上层标记 FutureAssisted。
 */
internal fun isPdfSource(fileName: String?, mimeType: String?): Boolean {
    if (mimeType != null && mimeType.equals("application/pdf", ignoreCase = true)) return true
    return fileName != null && fileName.substringAfterLast('.', "").equals("pdf", ignoreCase = true)
}

internal fun extractPdfSourceText(context: Context, uri: Uri): String? {
    return try {
        runCatching { PDFBoxResourceLoader.init(context.applicationContext) }
        val stream = context.contentResolver.openInputStream(uri) ?: return null
        stream.use { input: InputStream ->
            PDDocument.load(input).use { document ->
                if (document.isEncrypted) return null
                val text = PDFTextStripper().getText(document)
                text?.trim()
            }
        }
    } catch (t: Throwable) {
        null
    }
}
