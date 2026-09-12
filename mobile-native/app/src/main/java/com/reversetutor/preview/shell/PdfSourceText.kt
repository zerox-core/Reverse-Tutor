package com.reversetutor.preview.shell

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.rendering.PDFRenderer
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun isPdfSource(fileName: String?, mimeType: String?): Boolean {
    if (mimeType != null && mimeType.equals("application/pdf", ignoreCase = true)) return true
    return fileName != null && fileName.substringAfterLast('.', "").equals("pdf", ignoreCase = true)
}

private const val MaxScannedOcrPages = 60
private const val ScannedRenderDpi = 150f

/**
 * PDF 资料文本提取（V1-019 知识锚点）。
 *
 * 先走 PDF 自带文本层；文字层为空（扫描版，整页是图片）时，
 * 自动把每页渲染成图片、逐页跑离线文字识别（最多 [MaxScannedOcrPages] 页）。
 * 两条路都拿不到文字时返回 null，交由上层标记 FutureAssisted。
 */
internal suspend fun extractPdfSourceText(context: Context, uri: Uri): String? {
    return try {
        runCatching { PDFBoxResourceLoader.init(context.applicationContext) }
        val stream = context.contentResolver.openInputStream(uri) ?: return null
        stream.use { input: InputStream ->
            PDDocument.load(input).use { document ->
                if (document.isEncrypted) return null
                val layerText = PDFTextStripper().getText(document)?.trim()
                if (!layerText.isNullOrEmpty()) return layerText
                extractScannedPdfText(document)
            }
        }
    } catch (t: Throwable) {
        null
    }
}

private suspend fun extractScannedPdfText(document: PDDocument): String? {
    val pageCount = document.numberOfPages
    if (pageCount <= 0) return null
    val recognizer = newChineseTextRecognizer()
    return try {
        withContext(Dispatchers.IO) {
            val renderer = PDFRenderer(document)
            val limit = minOf(pageCount, MaxScannedOcrPages)
            val parts = mutableListOf<String>()
            for (pageIndex in 0 until limit) {
                val bitmap = try {
                    renderer.renderImageWithDPI(pageIndex, ScannedRenderDpi)
                } catch (t: Throwable) {
                    null
                } ?: continue
                try {
                    val text = recognizeText(recognizer, InputImage.fromBitmap(bitmap, 0))
                    if (!text.isNullOrEmpty()) parts.add(text)
                } finally {
                    bitmap.recycle()
                }
            }
            parts.joinToString("\n\n").trim().takeIf { it.isNotEmpty() }
        }
    } finally {
        recognizer.close()
    }
}
