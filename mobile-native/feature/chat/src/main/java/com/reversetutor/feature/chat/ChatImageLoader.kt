package com.reversetutor.feature.chat

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import kotlin.math.max

internal data class ChatImageTarget(
    val widthPixels: Int,
    val heightPixels: Int
)

internal object ChatImageTargets {
    val Thumbnail = ChatImageTarget(widthPixels = 720, heightPixels = 440)
}

internal fun calculateChatImageSampleSize(
    sourceWidth: Int,
    sourceHeight: Int,
    targetWidth: Int,
    targetHeight: Int
): Int {
    if (sourceWidth <= 0 || sourceHeight <= 0 || targetWidth <= 0 || targetHeight <= 0) return 1
    val maximumScaleDown = max(
        sourceWidth.toDouble() / targetWidth.toDouble(),
        sourceHeight.toDouble() / targetHeight.toDouble()
    )
    var sampleSize = 1
    while (sampleSize <= Int.MAX_VALUE / 2 && sampleSize * 2 <= maximumScaleDown) {
        sampleSize *= 2
    }
    return sampleSize
}

internal data class CachedChatImage<T>(
    val uri: String,
    val sampleSize: Int,
    val width: Int,
    val height: Int,
    val value: T
) {
    val pixelCount: Long = width.coerceAtLeast(0).toLong() * height.coerceAtLeast(0).toLong()
}

internal class ChatImageLruCache<T>(private val maxPixelCount: Long) {
    private data class Key(val uri: String, val sampleSize: Int)

    private val values = LinkedHashMap<Key, CachedChatImage<T>>(8, 0.75f, true)

    init {
        require(maxPixelCount > 0L)
    }

    val entryCount: Int
        @Synchronized get() = values.size

    val pixelCount: Long
        @Synchronized get() = values.values.sumOf(CachedChatImage<T>::pixelCount)

    @Synchronized
    fun get(uri: String, requestedSampleSize: Int): CachedChatImage<T>? {
        val reusableKey = values.keys
            .filter { it.uri == uri && it.sampleSize <= requestedSampleSize }
            .maxByOrNull(Key::sampleSize)
        return reusableKey?.let(values::get)
    }

    @Synchronized
    fun put(image: CachedChatImage<T>) {
        if (image.pixelCount <= 0L || image.pixelCount > maxPixelCount) return
        values.entries.removeAll { (key, _) ->
            key.uri == image.uri && key.sampleSize >= image.sampleSize
        }
        values[Key(image.uri, image.sampleSize)] = image
        val iterator = values.entries.iterator()
        while (values.values.sumOf(CachedChatImage<T>::pixelCount) > maxPixelCount && iterator.hasNext()) {
            iterator.next()
            iterator.remove()
        }
    }
}

internal sealed interface ChatImageLoadResult {
    data class Ready(val bitmap: ImageBitmap) : ChatImageLoadResult
    data class Failed(val reason: String) : ChatImageLoadResult
}

internal object ChatImageLoader {
    private const val CachePixelBudget = 8_000_000L
    private val cache = ChatImageLruCache<ImageBitmap>(CachePixelBudget)

    private sealed interface ChatImageStreamOutcome {
        data class Opened(val stream: InputStream) : ChatImageStreamOutcome
        data class Failed(val reason: String) : ChatImageStreamOutcome
    }

    @Synchronized
    fun load(context: Context, uri: Uri, target: ChatImageTarget): ChatImageLoadResult {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        when (val input = openChatImageStream(context, uri)) {
            is ChatImageStreamOutcome.Failed -> return ChatImageLoadResult.Failed(input.reason)
            is ChatImageStreamOutcome.Opened -> input.stream.use { stream ->
                runCatching { BitmapFactory.decodeStream(stream, null, bounds) }
                    .exceptionOrNull()
                    ?.let { error ->
                        return ChatImageLoadResult.Failed(
                            "图片文件读取失败：${error.message ?: error.javaClass.simpleName}"
                        )
                    }
            }
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return ChatImageLoadResult.Failed("图片格式无法识别（${describeUri(uri)}）")
        }

        val sampleSize = calculateChatImageSampleSize(
            sourceWidth = bounds.outWidth,
            sourceHeight = bounds.outHeight,
            targetWidth = target.widthPixels,
            targetHeight = target.heightPixels
        )
        val uriKey = uri.toString()
        cache.get(uriKey, sampleSize)?.let { return ChatImageLoadResult.Ready(it.value) }

        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = when (val input = openChatImageStream(context, uri)) {
            is ChatImageStreamOutcome.Failed -> return ChatImageLoadResult.Failed(input.reason)
            is ChatImageStreamOutcome.Opened -> input.stream.use { stream ->
                runCatching { BitmapFactory.decodeStream(stream, null, decodeOptions) }
                    .getOrElse { error ->
                        return ChatImageLoadResult.Failed(
                            "图片解码失败：${error.message ?: error.javaClass.simpleName}"
                        )
                    }
            }
        }
        val bitmap = decoded
            ?: return ChatImageLoadResult.Failed("图片解码返回空结果（${describeUri(uri)}），文件可能已损坏或丢失")
        val image = bitmap.asImageBitmap()
        cache.put(
            CachedChatImage(
                uri = uriKey,
                sampleSize = sampleSize,
                width = bitmap.width,
                height = bitmap.height,
                value = image
            )
        )
        return ChatImageLoadResult.Ready(image)
    }

    private fun describeUri(uri: Uri): String = uri.lastPathSegment ?: uri.toString()

    private fun openChatImageStream(context: Context, uri: Uri): ChatImageStreamOutcome {
        if (uri.scheme != "file") {
            return runCatching { context.contentResolver.openInputStream(uri) }.fold(
                onSuccess = { stream ->
                    stream?.let(ChatImageStreamOutcome::Opened)
                        ?: ChatImageStreamOutcome.Failed("内容提供器未返回图片流（${describeUri(uri)}）")
                },
                onFailure = { error ->
                    ChatImageStreamOutcome.Failed(
                        "图片流打开失败：${error.message ?: error.javaClass.simpleName}（${describeUri(uri)}）"
                    )
                }
            )
        }
        val path = uri.path ?: return ChatImageStreamOutcome.Failed("图片地址缺少文件路径（$uri）")
        return runCatching { FileInputStream(File(path)) }.fold(
            onSuccess = { ChatImageStreamOutcome.Opened(it) },
            onFailure = { error ->
                ChatImageStreamOutcome.Failed(
                    "图片文件打开失败：${error.message ?: error.javaClass.simpleName}"
                )
            }
        )
    }
}

internal fun openFeatureChatImageStream(context: Context, uri: Uri): InputStream? =
    if (uri.scheme == "file") {
        uri.path?.let(::File)?.let(::FileInputStream)
    } else {
        context.contentResolver.openInputStream(uri)
    }
