package com.reversetutor.core.domain

sealed interface OnlineData<out T> {
    data class Content<T>(val value: T) : OnlineData<T>
    data class Failure(val code: String, val retryable: Boolean) : OnlineData<Nothing>
}

data class OnlineAsset(
    val url: String,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val bytes: Long? = null,
    val sha256: String? = null
)

data class OnlineContentSummary(
    val id: String,
    val slug: String,
    val type: String,
    val title: String,
    val summary: String,
    val illustrationTemplate: String,
    val illustrationDialogues: List<String>,
    val illustrationPalette: String?,
    val cover: OnlineAsset?,
    val publisherName: String?,
    val publishedAtEpochMillis: Long,
    val contentVersion: Long
)

data class OnlineContentPage(
    val version: Long,
    val updatedAtEpochMillis: Long,
    val items: List<OnlineContentSummary>,
    val nextCursor: String?
)

data class OnlineContentArticle(
    val summary: OnlineContentSummary,
    val bodyMarkdown: String,
    val bodyAssets: List<OnlineAsset>
)

interface ContentRepository {
    suspend fun feed(
        cursor: String? = null,
        limit: Int = 20,
        types: Set<String> = emptySet(),
        etag: String? = null
    ): OnlineData<OnlineContentPage>

    suspend fun detail(slug: String): OnlineData<OnlineContentArticle>
}
