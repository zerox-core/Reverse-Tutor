package com.reversetutor.preview.shell

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * 图片资料文字提取（V1-019 知识锚点：本地 OCR）。
 *
 * 使用设备内置的 ML Kit 中文文字识别，全程离线、不依赖 AI 渠道；
 * 识别失败或图片没有可读文字时返回 null，交由上层标记 FutureAssisted。
 */
internal fun isImageSource(fileName: String?, mimeType: String?): Boolean {
    if (mimeType != null && mimeType.startsWith("image/", ignoreCase = true)) return true
    val ext = fileName?.lowercase()?.substringAfterLast('.', "") ?: return false
    return ext in setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")
}

internal suspend fun extractImageSourceText(context: Context, uri: Uri): String? {
    return try {
        val image = InputImage.fromFilePath(context, uri)
        val recognizer = TextRecognition.getClient(
            ChineseTextRecognizerOptions.Builder().build()
        )
        val visionText = try {
            suspendCancellableCoroutine { continuation ->
                recognizer.process(image)
                    .addOnSuccessListener { text -> continuation.resume(text) }
                    .addOnFailureListener { error -> continuation.resumeWithException(error) }
            }
        } finally {
            recognizer.close()
        }
        visionText.text.trim().takeIf { it.isNotEmpty() }
    } catch (t: Throwable) {
        null
    }
}
