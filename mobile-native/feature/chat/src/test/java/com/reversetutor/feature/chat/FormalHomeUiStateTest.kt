package com.reversetutor.feature.chat

import com.reversetutor.core.domain.OnlineContentPage
import com.reversetutor.core.domain.OnlineContentSummary
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormalHomeUiStateTest {
    @Test
    fun visibleSessionsPutPinnedFirstThenUseLatestUpdate() {
        val state = FormalHomeUiState(
            publicContent = publicContent(),
            sessions = listOf(
                session(id = "old", updatedAt = 10L),
                session(id = "latest", updatedAt = 40L),
                session(id = "pinned-old", updatedAt = 20L, pinned = true),
                session(id = "pinned-new", updatedAt = 30L, pinned = true),
                session(id = "hidden-fifth", updatedAt = 50L)
            )
        )

        assertEquals(
            listOf("pinned-new", "pinned-old", "hidden-fifth", "latest"),
            state.visibleSessions.map { it.id }
        )
        assertTrue(state.visibleSessions.take(2).all { it.pinned })
    }

    @Test
    fun variantIsDerivedFromChallengeAndSheetState() {
        val defaultState = FormalHomeUiState(publicContent = publicContent())
        val challengeState = defaultState.copy(challenge = challenge())
        val sheetState = challengeState.copy(isNewSessionSheetVisible = true)

        assertEquals(FormalHomeVariant.Default, defaultState.variant)
        assertEquals(FormalHomeVariant.JoinedChallenge, challengeState.variant)
        assertEquals(FormalHomeVariant.NewSessionSheet, sheetState.variant)
    }

    @Test
    fun sessionListAdapterKeepsLocalContentWithoutPreviewFallbacks() {
        val source = SessionListUiState(
            visibleSessions = listOf(
                SessionListItem(
                    id = "local-session",
                    title = "本地会话",
                    updatedAtEpochMillis = 120_000L,
                    pinned = false,
                    statusLabel = "真实本地摘要",
                    unreadCount = 0,
                    avatarLabel = "已隐藏"
                )
            ),
            query = "",
            filter = SessionListFilter.All,
            summary = "1 个会话",
            emptyStateTitle = ""
        )

        val result = source.toFormalHomeUiState(
            publicContent = publicContent(),
            nowEpochMillis = 180_000L
        )

        assertEquals("本地会话", result.visibleSessions.single().title)
        assertEquals("真实本地摘要", result.visibleSessions.single().summary)
        assertEquals("1 分钟前", result.visibleSessions.single().timeLabel)
        assertFalse(result.visibleSessions.single().pinned)
    }

    @Test
    fun onlineFeedMapsRealIdentityAndSlugForArticleNavigation() {
        val result = OnlineContentPage(
            version = 7,
            updatedAtEpochMillis = 1_784_160_000_000,
            items = listOf(
                onlineContent(
                    id = "content-42",
                    slug = "verify-before-opening-links",
                    title = "别让陌生链接替你做决定"
                ),
                onlineContent(
                    id = "content-43",
                    slug = "read-with-children",
                    title = "放学以后，一起读书"
                )
            ),
            nextCursor = null
        ).toFormalPublicContentUi()

        requireNotNull(result)
        assertEquals("content-42", result.id)
        assertEquals("verify-before-opening-links", result.slug)
        assertEquals("别让陌生链接替你做决定", result.title)
        assertEquals("7月12日 · 公益编辑部", result.publishedLabel)
        assertEquals("1 / 2", result.pageLabel)
        assertTrue(result.canOpen)
    }

    @Test
    fun emptyFeedAndUnavailableStatesNeverExposeArticleNavigation() {
        val empty = OnlineContentPage(
            version = 1,
            updatedAtEpochMillis = 0,
            items = emptyList(),
            nextCursor = null
        ).toFormalPublicContentUi()

        assertNull(empty)
        assertFalse(FormalPublicContentUi.loading().canOpen)
        assertFalse(FormalPublicContentUi.offline().canOpen)
        assertFalse(FormalPublicContentUi.unavailable().canOpen)
    }

    @Test
    fun newSessionModesExposeOnlyTheApprovedLearningFlow() {
        val modes = formalNewSessionModes()

        assertEquals(
            listOf(NewSessionMode.Learning, NewSessionMode.Review, NewSessionMode.Companion),
            modes.map { it.mode }
        )
        assertTrue(modes.single { it.mode == NewSessionMode.Learning }.enabled)
        assertFalse(modes.single { it.mode == NewSessionMode.Review }.enabled)
        assertFalse(modes.single { it.mode == NewSessionMode.Companion }.enabled)
    }

    @Test
    fun homeSessionRowsUseStableExpandedSpacingWithoutCardShadow() {
        assertEquals(80.dp, HomeSessionLayout.RegularHeight)
        assertEquals(92.dp, HomeSessionLayout.PinnedHeight)
        assertEquals(14.dp, HomeSessionLayout.HorizontalPadding)
        assertEquals(34.dp, HomeSessionLayout.AvatarSize)
        assertEquals(10.dp, HomeSessionLayout.AvatarGap)
        assertEquals(82.dp, HomeSessionLayout.TrailingReserve)
        assertEquals(0.dp, HomeSessionLayout.Elevation)
        assertEquals(112.dp, HomeSessionLayout.ChallengePullThreshold)
    }

    @Test
    fun homeSessionRowsExposeStableAvatarAndLongPressActions() {
        val source = SessionListUiState(
            visibleSessions = listOf(
                SessionListItem(
                    id = "session-1",
                    title = "高三数学讲题冲刺",
                    updatedAtEpochMillis = 10L,
                    pinned = false,
                    statusLabel = "继续讲解",
                    unreadCount = 0,
                    avatarLabel = "高"
                )
            ),
            query = "",
            filter = SessionListFilter.All,
            summary = "1 个会话",
            emptyStateTitle = ""
        )

        val row = source.toFormalHomeUiState(publicContent(), nowEpochMillis = 10L)
            .visibleSessions
            .single()

        assertEquals("高", row.avatarLabel)
        assertEquals(
            listOf(
                HomeSessionAction.Rename,
                HomeSessionAction.Pin,
                HomeSessionAction.Export,
                HomeSessionAction.Delete
            ),
            HomeSessionAction.entries
        )
    }

    private fun publicContent() = FormalPublicContentUi(
        id = "public-1",
        title = "陌生链接，先停一下",
        summary = "三个小对话，学会识别网络诈骗",
        publishedLabel = "7月12日 · Reverse Tutor",
        pageLabel = "1 / 4"
    )

    private fun challenge() = FormalJoinedChallengeUi(
        id = "challenge-1",
        title = "21 天学习挑战",
        dayLabel = "挑战进行中 · 第 8 天",
        todayPrompt = "今天：用三句话讲清楚机会成本",
        progressFraction = 0.38f
    )

    private fun onlineContent(
        id: String,
        slug: String,
        title: String
    ) = OnlineContentSummary(
        id = id,
        slug = slug,
        type = "public_interest",
        title = title,
        summary = "三个对话，识别常见网络诈骗",
        illustrationTemplate = "dialogue-security-01",
        illustrationDialogues = emptyList(),
        illustrationPalette = null,
        cover = null,
        publisherName = "公益编辑部",
        publishedAtEpochMillis = 1_783_814_400_000,
        contentVersion = 2
    )

    private fun session(
        id: String,
        updatedAt: Long,
        pinned: Boolean = false
    ) = FormalHomeSessionUi(
        id = id,
        title = id,
        summary = "summary-$id",
        timeLabel = "刚刚",
        updatedAtEpochMillis = updatedAt,
        pinned = pinned
    )
}
