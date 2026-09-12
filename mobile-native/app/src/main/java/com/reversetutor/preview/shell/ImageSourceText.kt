package com.reversetutor.preview.shell

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * 图片资料文字提取（V1-019 知识锚点：本地 OCR）。
 *
 * 使用设备内置的 ML Kit 中文文字识别，全程离线、不依赖 AI 渠道；
 * 识别失败或图片没有可读文字时返回 null，交由上层标记 FutureAssisted。
 * 识别器与单张识别逻辑同时供扫描版 PDF 逐页识别复用。
 */
internal fun isImageSource(fileName: String?, mimeType: String?): Boolean {
    if (mimeType != null && mimeType.startsWith("image/", ignoreCase = true)) return true
    val ext = fileName?.lowercase()?.substringAfterLast('.', "") ?: return false
    return ext in setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")
}

internal fun newChineseTextRecognizer(): TextRecognizer =
    TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())

internal suspend fun recognizeText(recognizer: TextRecognizer, image: InputImage): String? {
    return try {
        suspendCancellableCoroutine { continuation ->
            recognizer.process(image)
                .addOnSuccessListener { text -> continuation.resume(text) }
                .addOnFailureListener { error -> continuation.resumeWithException(error) }
        }.text.trim().takeIf { it.isNotEmpty() }
    } catch (t: Throwable) {
        null
    }
}

internal suspend fun extractImageSourceText(context: Context, uri: Uri): String? {
    return try {
        val image = InputImage.fromFilePath(context, uri)
        val recognizer = newChineseTextRecognizer()
        try {
            recognizeText(recognizer, image)
        } finally {
            recognizer.close()
        }
    } catch (t: Throwable) {
        null
    }
}
