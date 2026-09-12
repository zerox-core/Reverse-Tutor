package com.reversetutor.preview.shell

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.reversetutor.core.data.llm.ChatGenerationRepository
import java.io.ByteArrayInputStream
import java.io.File

/**
 * Word / PPT / 电子书资料导入的 Android 侧包装（NEWMP-V1-021；V1-023 起
 * PPTX / EPUB 解析复用本文件的嵌入图转写链）。
 *
 * 解析逻辑在 DocxSourceText.kt（纯 JVM、可单测）；这里负责：
 * 1. 从 content:// 打开文件喂给解析器；
 * 2. 嵌入图片的转写——先解码成位图跑本地 OCR（免费、逐字准）；
 *    OCR 读不出且用户开了「云端看图转写」时，把图压缩后写临时文件，
 *    走与图片资料相同的云端多模态转写，用完即删。
 */

/** 发给云端前把大图压到该边长以内：上传更小、转写更快，且远低于 20MB 上限。 */
private const val VisionMaxDimension = 1600

private const val VisionJpegQuality = 85

internal suspend fun extractDocxSourceText(
    context: Context,
    uri: Uri,
    transcribeImage: suspend (DocxEmbeddedImage) -> String?
): String? {
    return try {
        val stream = context.contentResolver.openInputStream(uri) ?: return null
        stream.use { input -> extractDocxSourceText(input, transcribeImage) }
    } catch (t: Throwable) {
        null
    }
}

/**
 * 单张嵌入图的转写链：解码 → 本地 OCR → 云端多模态（V1-022 起永久开启）。
 * NEWMP-V1-023：不再按尺寸跳过任何图片——小图照样走 OCR/云端转写；
 * 只压缩画质（大图压到 1600px 以内），不牺牲功能。
 * 任何一步失败都返回 null，不影响正文其他部分。
 */
internal suspend fun transcribeDocxEmbeddedImage(
    context: Context,
    repository: ChatGenerationRepository,
    sessionId: String?,
    visionAssistEnabled: Boolean,
    visionModelName: String,
    image: DocxEmbeddedImage
): String? {
    val bitmap = runCatching {
        BitmapFactory.decodeStream(ByteArrayInputStream(image.bytes))
    }.getOrNull() ?: return null
    val recognizer = newChineseTextRecognizer()
    val ocrText = try {
        recognizeText(recognizer, InputImage.fromBitmap(bitmap, 0))
    } finally {
        recognizer.close()
    }
    if (!ocrText.isNullOrEmpty()) {
        bitmap.recycle()
        return ocrText
    }
    if (!visionAssistEnabled || sessionId == null) {
        bitmap.recycle()
        return null
    }
    val tempFile = writeVisionTempImage(context, bitmap)
    bitmap.recycle()
    if (tempFile == null) return null
    return try {
        describeSourceImageWithVision(
            repository = repository,
            sessionId = sessionId,
            fileName = image.partName.substringAfterLast('/'),
            mimeType = if (tempFile.extension == "png") "image/png" else "image/jpeg",
            uri = Uri.fromFile(tempFile).toString(),
            visionModelName = visionModelName
        )
    } finally {
        tempFile.delete()
    }
}

/** 压缩后写入 cacheDir/vision-tmp，供云端转写读取；调用方负责删除。 */
private fun writeVisionTempImage(context: Context, bitmap: Bitmap): File? {
    val largest = maxOf(bitmap.width, bitmap.height)
    val scaled = if (largest > VisionMaxDimension) {
        val scale = VisionMaxDimension.toFloat() / largest
        runCatching {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true
            )
        }.getOrNull()
    } else {
        null
    }
    val source = scaled ?: bitmap
    val hasAlpha = source.hasAlpha()
    val dir = File(context.cacheDir, "vision-tmp").apply { mkdirs() }
    val file = File(dir, "docx-${System.currentTimeMillis()}-${System.nanoTime()}.${if (hasAlpha) "png" else "jpg"}")
    return try {
        file.outputStream().use { out ->
            source.compress(
                if (hasAlpha) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG,
                VisionJpegQuality,
                out
            )
        }
        scaled?.recycle()
        file.takeIf { it.length() > 0 }
    } catch (t: Throwable) {
        scaled?.recycle()
        file.delete()
        null
    }
}
