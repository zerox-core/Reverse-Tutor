package com.reversetutor.core.remote

data class OnlineIllustrationConfig(
    val dialogues: List<String> = emptyList(),
    val palette: String? = null
)

data class OnlineAssetRef(
    val url: String,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val bytes: Long? = null,
    val sha256: String? = null
)

data class OnlineContentItem(
    val id: String,
    val slug: String,
    val type: String,
    val title: String,
    val summary: String,
    val illustrationTemplate: String,
    val illustration: OnlineIllustrationConfig,
    val cover: OnlineAssetRef?,
    val publisherName: String?,
    val publishedAtEpochMillis: Long,
    val contentVersion: Long
)

data class ContentFeedPage(
    val version: Long,
    val updatedAtEpochMillis: Long,
    val items: List<OnlineContentItem>,
    val nextCursor: String?
)

data class OnlineContentDetail(
    val item: OnlineContentItem,
    val bodyMarkdown: String,
    val bodyAssets: List<OnlineAssetRef>
) {
    val id: String get() = item.id
    val slug: String get() = item.slug
    val title: String get() = item.title
}
