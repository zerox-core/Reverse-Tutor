package com.reversetutor.preview.shell

import com.reversetutor.core.domain.OnlineContentArticle
import com.reversetutor.core.domain.OnlineContentSummary
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FormalSearchArticleRuntimeTest {
    private lateinit var previousTimeZone: TimeZone

    @Before
    fun useUtc() {
        previousTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun restoreTimeZone() {
        TimeZone.setDefault(previousTimeZone)
    }

    @Test
    fun onlineArticleMapsServerFieldsWithoutPreviewSections() {
        val state = OnlineContentArticle(
            summary = OnlineContentSummary(
                id = "article-1",
                slug = "quiet-reading",
                type = "public_interest",
                title = "安静阅读",
                summary = "真实摘要",
                illustrationTemplate = "none",
                illustrationDialogues = emptyList(),
                illustrationPalette = null,
                cover = null,
                publisherName = "公益发布方",
                publishedAtEpochMillis = 0L,
                contentVersion = 1L
            ),
            bodyMarkdown = "第一段。\n\n第二段。",
            bodyAssets = emptyList()
        ).toFormalArticleUiState()

        assertEquals("安静阅读", state.title)
        assertEquals("公益发布方", state.publisher)
        assertEquals("1970年1月1日", state.publishLabel)
        assertEquals(listOf("第一段。"), state.bodyParagraphs)
        assertEquals(listOf("第二段。"), state.continuationParagraphs)
        assertTrue(state.quote.isBlank())
        assertTrue(state.relatedArticles.isEmpty())
    }
}
