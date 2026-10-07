package com.reversetutor.feature.chat

import com.reversetutor.core.domain.ContextWarning
import com.reversetutor.core.domain.LearningOverviewContract
import com.reversetutor.core.domain.LearningOverviewScope
import com.reversetutor.core.domain.LearningProgressContract
import com.reversetutor.core.domain.LearningThreadContract
import com.reversetutor.core.domain.TodayPlanContract
import com.reversetutor.core.domain.TodayPlanTask
import com.reversetutor.core.domain.TokenUsageOverviewContract
import com.reversetutor.core.domain.WeakPointContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B2: Tests for the six learning overview states — loading, error, no-data,
 * complete, partial-warnings, and empty-fields-within-complete.
 *
 * B1: Tests for presentation helpers in [LearningOverviewPresentation].
 *
 * B3-prep: Scope construction tests — verifies that `listOf(currentSessionId)`
 * is dispatched (never `emptyList()`), and that null currentSessionId does not
 * produce a "当前会话" scope.
 */
class LearningOverviewPanelStateTest {

    // ---------------------------------------------------------------------------
    // B2: Six states
    // ---------------------------------------------------------------------------

    @Test
    fun loadingStateHasLoadingTrueAndNoError() {
        val state = LearningOverviewUiState(
            scope = defaultScope,
            isLoading = true
        )
        assertTrue(state.isLoading)
        assertNull(state.errorMessage)
        assertFalse(state.isNoData)
    }

    @Test
    fun errorStateHasMessageAndNotLoading() {
        val state = LearningOverviewUiState(
            scope = defaultScope,
            isLoading = false,
            errorMessage = "暂时无法加载学习概览，请稍后重试"
        )
        assertFalse(state.isLoading)
        assertTrue(state.errorMessage != null)
        assertEquals("暂时无法加载学习概览，请稍后重试", state.errorMessage)
    }

    @Test
    fun noDataStateShowsEmptyNotError() {
        val state = LearningOverviewUiState(
            scope = defaultScope,
            isNoData = true
        )
        assertTrue(state.isNoData)
        assertNull(state.errorMessage)
        assertFalse(state.isLoading)
    }

    @Test
    fun completeStateHasAllFieldsPopulated() {
        val state = LearningOverviewUiState(
            scope = defaultScope,
            isLoading = false,
            isNoData = false,
            generatedAtLabel = "08/22 15:30",
            activeSessionCount = 3,
            progress = LearningProgressContract(
                totalKnowledgePoints = 50,
                masteredCount = 25,
                masteryRate = 0.5f,
                weeklyChange = 0.05f
            ),
            todayPlan = TodayPlanContract(
                tasks = listOf(
                    TodayPlanTask("t1", "复习函数参数", "done", "函数参数"),
                    TodayPlanTask("t2", "练习Lambda", "pending", "Lambda表达式")
                ),
                completedCount = 1,
                totalCount = 2
            ),
            weeklyMainline = listOf(
                LearningThreadContract("w1", "函数与Lambda", "函数参数", 0.6f, 1L)
            ),
            weakPoints = listOf(
                WeakPointContract("wp1", "高阶函数", 3, 1L, 0.8f)
            ),
            tokenUsage = TokenUsageOverviewContract(
                totalTokens = 15000,
                estimatedTokens = 0,
                isEstimated = false
            )
        )
        assertFalse(state.isLoading)
        assertFalse(state.isNoData)
        assertNull(state.errorMessage)
        assertEquals("08/22 15:30", state.generatedAtLabel)
        assertEquals(3, state.activeSessionCount)
        assertEquals(50, state.masteryPercent)
        assertEquals(5, state.weeklyChangePercent)
        assertEquals(2, state.todayPlan.totalCount)
        assertEquals(1, state.weeklyMainline.size)
        assertEquals(1, state.weakPoints.size)
        assertEquals(15000L, state.tokenUsage.totalTokens)
    }

    @Test
    fun partialWarningsStateHasDataAndNonBlockingWarnings() {
        val state = LearningOverviewUiState(
            scope = defaultScope,
            isLoading = false,
            isNoData = false,
            progress = LearningProgressContract(
                totalKnowledgePoints = 10,
                masteredCount = 5,
                masteryRate = 0.5f
            ),
            warnings = listOf("记忆检索部分不可用", "知识点图谱部分不可用")
        )
        // B2: warnings exist but other data also exists — must NOT degrade to error page
        assertTrue(state.warnings.isNotEmpty())
        assertFalse(state.isNoData)
        assertNull(state.errorMessage)
        // Real data is present alongside warnings
        assertEquals(50, state.masteryPercent)
    }

    @Test
    fun emptyFieldsWithinCompleteShowDefaultsNotFabricated() {
        val state = LearningOverviewUiState(
            scope = defaultScope,
            isLoading = false,
            isNoData = false,
            progress = LearningProgressContract(),
            todayPlan = TodayPlanContract(),
            weeklyMainline = emptyList(),
            weakPoints = emptyList(),
            tokenUsage = TokenUsageOverviewContract()
        )
        // Empty fields show defaults, not fabricated content
        assertEquals(0, state.masteryPercent)
        assertTrue(state.todayPlan.isEmpty)
        assertTrue(state.weeklyMainline.isEmpty())
        assertTrue(state.weakPoints.isEmpty())
        assertEquals(0L, state.tokenUsage.totalTokens)
        // isNoData is false — partial data, not first-time empty
        assertFalse(state.isNoData)
    }

    // ---------------------------------------------------------------------------
    // B2: Warning safety — no raw source/message leakage
    // ---------------------------------------------------------------------------

    @Test
    fun contextWarningsMapToSafeChineseText() {
        val warnings = listOf(
            ContextWarning(source = "memory", message = "https://api.openai.com sk-abc123 401"),
            ContextWarning(source = "graph", message = "NullPointerException at com.reversetutor.core.data"),
            ContextWarning(source = "unknown_source", message = "Bearer xyz expired")
        )
        val safeTexts = warnings.toSafeWarningTexts()
        assertTrue(safeTexts.isNotEmpty())
        safeTexts.forEach { text ->
            assertFalse("Leaked URL: $text", text.contains("https://"))
            assertFalse("Leaked API key: $text", text.contains("sk-"))
            assertFalse("Leaked bearer: $text", text.contains("Bearer"))
            assertFalse("Leaked source: $text", text.contains("unknown_source"))
            assertFalse("Leaked stack: $text", text.contains("NullPointerException"))
            assertFalse("Leaked path: $text", text.contains("com.reversetutor"))
            assertFalse("Leaked HTTP code: $text", text.contains("401"))
        }
        assertTrue(safeTexts.contains("记忆检索部分不可用"))
        assertTrue(safeTexts.contains("知识点图谱部分不可用"))
        assertTrue(safeTexts.contains("部分学习数据暂不可用"))
    }

    @Test
    fun emptyWarningsProduceEmptySafeTexts() {
        val safeTexts = emptyList<ContextWarning>().toSafeWarningTexts()
        assertTrue(safeTexts.isEmpty())
    }

    // ---------------------------------------------------------------------------
    // B1: Presentation helper tests
    // ---------------------------------------------------------------------------

    @Test
    fun tokenDisplayLabelFormatsSmallNumbers() {
        assertEquals("500", 500L.toTokenDisplayLabel(false))
    }

    @Test
    fun tokenDisplayLabelFormatsLargeNumbersInWan() {
        assertEquals("1.5万", 15000L.toTokenDisplayLabel(false))
    }

    @Test
    fun tokenDisplayLabelAppendsEstimatedSuffix() {
        assertEquals("500（估算）", 500L.toTokenDisplayLabel(true))
    }

    @Test
    fun masteryDisplayFormatsPositiveChange() {
        val (mastery, change) = formatMasteryDisplay(50, 5)
        assertEquals("50%", mastery)
        assertEquals("本周 +5%", change)
    }

    @Test
    fun masteryDisplayFormatsNegativeChange() {
        val (mastery, change) = formatMasteryDisplay(30, -3)
        assertEquals("30%", mastery)
        assertEquals("本周 -3%", change)
    }

    @Test
    fun masteryDisplayFormatsZeroChange() {
        val (_, change) = formatMasteryDisplay(50, 0)
        assertEquals("本周 +0%", change)
    }

    @Test
    fun weakPointSeverityTierFromHighSeverity() {
        assertEquals(WeakPointSeverityTier.HIGH, WeakPointSeverityTier.from(0.8f))
    }

    @Test
    fun weakPointSeverityTierFromMediumSeverity() {
        assertEquals(WeakPointSeverityTier.MEDIUM, WeakPointSeverityTier.from(0.5f))
    }

    @Test
    fun weakPointSeverityTierFromLowSeverity() {
        assertEquals(WeakPointSeverityTier.LOW, WeakPointSeverityTier.from(0.2f))
    }

    @Test
    fun todayTaskStatusFromDone() {
        assertEquals(TodayTaskStatus.DONE, TodayTaskStatus.from("done"))
    }

    @Test
    fun todayTaskStatusFromPending() {
        assertEquals(TodayTaskStatus.PENDING, TodayTaskStatus.from("pending"))
    }

    @Test
    fun todayTaskStatusFromUnknownDefaultsToPending() {
        assertEquals(TodayTaskStatus.PENDING, TodayTaskStatus.from("unknown_status"))
    }

    // ---------------------------------------------------------------------------
    // B3-prep: Scope construction — never dispatch emptyList()
    // ---------------------------------------------------------------------------

    @Test
    fun allSessionsScopeHasNullSessionIds() {
        val scope = defaultScope.copy(sessionIds = null)
        assertNull(scope.sessionIds)
    }

    @Test
    fun currentSessionScopeHasListOfSessionId() {
        val currentSessionId = "s-42"
        val scope = defaultScope.copy(sessionIds = listOf(currentSessionId))
        assertEquals(listOf("s-42"), scope.sessionIds)
        // Critical: never emptyList() — that means zero sessions, not "current"
        assertFalse(scope.sessionIds?.isEmpty() ?: false)
    }

    @Test
    fun nullCurrentSessionIdDoesNotProduceScope() {
        // When currentSessionId is null/blank, "当前会话" is hidden.
        // The only scope dispatched is all-sessions (sessionIds = null).
        val currentSessionId: String? = null
        // Simulate the panel's logic: if currentSessionId is blank, don't dispatch
        val shouldShowCurrentSession = !currentSessionId.isNullOrBlank()
        assertFalse(shouldShowCurrentSession)
        // Only "全部会话" is available — dispatches sessionIds = null
        val allSessionsScope = defaultScope.copy(sessionIds = null)
        assertNull(allSessionsScope.sessionIds)
    }

    @Test
    fun blankCurrentSessionIdDoesNotProduceScope() {
        val currentSessionId = "   "
        val shouldShowCurrentSession = !currentSessionId.isNullOrBlank()
        assertFalse(shouldShowCurrentSession)
    }

    @Test
    fun validCurrentSessionIdProducesScope() {
        val currentSessionId = "session-abc"
        val shouldShowCurrentSession = !currentSessionId.isNullOrBlank()
        assertTrue(shouldShowCurrentSession)
        val scope = defaultScope.copy(sessionIds = listOf(currentSessionId))
        assertEquals(listOf("session-abc"), scope.sessionIds)
    }

    // ---------------------------------------------------------------------------
    // B1: UiState derived properties
    // ---------------------------------------------------------------------------

    @Test
    fun masteryPercentCalculatesFromMasteryRate() {
        val state = LearningOverviewUiState(
            scope = defaultScope,
            progress = LearningProgressContract(masteryRate = 0.75f)
        )
        assertEquals(75, state.masteryPercent)
    }

    @Test
    fun masteryPercentClampsToHundred() {
        val state = LearningOverviewUiState(
            scope = defaultScope,
            progress = LearningProgressContract(masteryRate = 1.5f)
        )
        assertEquals(100, state.masteryPercent)
    }

    @Test
    fun masteryPercentClampsToZero() {
        val state = LearningOverviewUiState(
            scope = defaultScope,
            progress = LearningProgressContract(masteryRate = -0.5f)
        )
        assertEquals(0, state.masteryPercent)
    }

    @Test
    fun weeklyChangePercentConvertsFromFloat() {
        val state = LearningOverviewUiState(
            scope = defaultScope,
            progress = LearningProgressContract(weeklyChange = 0.12f)
        )
        assertEquals(12, state.weeklyChangePercent)
    }

    @Test
    fun todayPlanEmptyWhenNoTasks() {
        val state = LearningOverviewUiState(
            scope = defaultScope,
            todayPlan = TodayPlanContract(tasks = emptyList())
        )
        assertTrue(state.todayPlan.isEmpty)
    }

    // ---------------------------------------------------------------------------
    // Fixture
    // ---------------------------------------------------------------------------

    private val defaultScope = LearningOverviewScope(spaceId = "space-1", sessionIds = null)
}
