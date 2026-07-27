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

internal object ChatImageLoader {
    private const val CachePixelBudget = 8_000_000L
    private val cache = ChatImageLruCache<ImageBitmap>(CachePixelBudget)

    @Synchronized
    fun load(context: Context, uri: Uri, target: ChatImageTarget): ImageBitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openFeatureChatImageStream(context, uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val sampleSize = calculateChatImageSampleSize(
            sourceWidth = bounds.outWidth,
            sourceHeight = bounds.outHeight,
            targetWidth = target.widthPixels,
            targetHeight = target.heightPixels
        )
        val uriKey = uri.toString()
        cache.get(uriKey, sampleSize)?.let { return it.value }

        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bitmap = openFeatureChatImageStream(context, uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, decodeOptions)
        } ?: return null
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
        return image
    }
}

internal fun openFeatureChatImageStream(context: Context, uri: Uri): InputStream? =
    if (uri.scheme == "file") {
        uri.path?.let(::File)?.let(::FileInputStream)
    } else {
        context.contentResolver.openInputStream(uri)
    }
