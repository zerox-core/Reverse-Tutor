@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.reversetutor.feature.chat

import com.reversetutor.core.domain.LearningOverviewContract
import com.reversetutor.core.domain.LearningOverviewScope
import com.reversetutor.core.domain.LearningProgressContract
import com.reversetutor.core.domain.LearningThreadContract
import com.reversetutor.core.domain.ContextWarning
import com.reversetutor.core.domain.TodayPlanContract
import com.reversetutor.core.domain.TodayPlanTask
import com.reversetutor.core.domain.TokenUsageOverviewContract
import com.reversetutor.core.domain.WeakPointContract
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningOverviewViewModelTest {

    private val defaultScope = LearningOverviewScope(spaceId = "space-1", sessionIds = null)

    @Test
    fun loadsAndProjectsContract() = runTest {
        val port = FakeLearningOverviewPort(overview = sampleOverview())
        val viewModel = LearningOverviewViewModel(
            port = port,
            initialScope = defaultScope,
            scope = this
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
        assertFalse(state.isNoData)
        assertEquals(3, state.activeSessionCount)
        assertEquals(40, state.masteryPercent)
        assertEquals(12, state.weeklyChangePercent)
        assertEquals(2, state.todayPlan.totalCount)
        assertEquals(1, state.todayPlan.completedCount)
        assertEquals(1, state.weeklyMainline.size)
        assertEquals(1, state.weakPoints.size)
        assertEquals(12_345L, state.tokenUsage.totalTokens)
        assertTrue(state.generatedAtLabel.isNotBlank())
    }

    @Test
    fun reportsLoadingWhileFetchPending() = runTest {
        val gate = CompletableDeferred<Unit>()
        val port = object : LearningOverviewPort {
            override suspend fun loadOverview(scope: LearningOverviewScope): LearningOverviewContract {
                gate.await()
                return sampleOverview()
            }
        }
        val viewModel = LearningOverviewViewModel(
            port = port,
            initialScope = defaultScope,
            scope = this
        )
        runCurrent()
        assertTrue(viewModel.uiState.value.isLoading)

        gate.complete(Unit)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun errorStateRedactsRawFailureDetails() = runTest {
        val port = FakeLearningOverviewPort(overview = sampleOverview())
            .apply {
                loadError = IllegalStateException(
                    "https://provider.example Authorization: Bearer sk-test-secret"
                )
            }
        val viewModel = LearningOverviewViewModel(
            port = port,
            initialScope = defaultScope,
            scope = this
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("暂时无法加载学习概览，请稍后重试", state.errorMessage)
        assertFalse(state.errorMessage.orEmpty().contains("https://"))
        assertFalse(state.errorMessage.orEmpty().contains("Authorization"))
        assertFalse(state.errorMessage.orEmpty().contains("sk-"))
    }

    @Test
    fun warningMessagesAreWhitelistedAndDoNotExposeSourcePayload() = runTest {
        val port = FakeLearningOverviewPort(
            overview = sampleOverview().copy(
                warnings = listOf(
                    ContextWarning("tokenUsage", "https://provider.example sk-test-secret"),
                    ContextWarning("unknown", "Authorization: Bearer secret")
                )
            )
        )
        val viewModel = LearningOverviewViewModel(
            port = port,
            initialScope = defaultScope,
            scope = this
        )
        advanceUntilIdle()

        val warnings = viewModel.uiState.value.warnings
        assertEquals(listOf("Token 用量暂不可用", "部分学习数据暂不可用"), warnings)
        assertFalse(warnings.joinToString().contains("https://"))
        assertFalse(warnings.joinToString().contains("Authorization"))
        assertFalse(warnings.joinToString().contains("sk-"))
    }

    @Test
    fun noDataStateIsProjected() = runTest {
        val noData = sampleOverview().copy(isNoData = true)
        val port = FakeLearningOverviewPort(overview = noData)
        val viewModel = LearningOverviewViewModel(
            port = port,
            initialScope = defaultScope,
            scope = this
        )
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isNoData)
    }

    @Test
    fun scopeSwitchTriggersReloadWithNewScope() = runTest {
        val port = FakeLearningOverviewPort(overview = sampleOverview())
        val viewModel = LearningOverviewViewModel(
            port = port,
            initialScope = defaultScope,
            scope = this
        )
        advanceUntilIdle()

        val subset = LearningOverviewScope(spaceId = "space-1", sessionIds = listOf("session-A"))
        viewModel.onAction(LearningOverviewUiAction.ChangeScope(subset))
        runCurrent()

        assertEquals(subset, viewModel.uiState.value.scope)
        advanceUntilIdle()
        assertEquals(subset, port.lastLoadedScope)
    }

    private fun sampleOverview(): LearningOverviewContract = LearningOverviewContract(
        scope = defaultScope,
        generatedAtEpochMillis = 1_000L,
        activeSessionCount = 3,
        progress = LearningProgressContract(
            totalKnowledgePoints = 10,
            masteredCount = 4,
            masteryRate = 0.4f,
            weeklyChange = 0.12f
        ),
        todayPlan = TodayPlanContract(
            tasks = listOf(
                TodayPlanTask("task-1", "回顾函数参数", "done", "函数参数"),
                TodayPlanTask("task-2", "练习返回值", "pending", "返回值")
            ),
            completedCount = 1,
            totalCount = 2
        ),
        weeklyMainline = listOf(
            LearningThreadContract("thread-1", "函数与参数", "函数参数", 0.5f, 1L)
        ),
        weakPoints = listOf(
            WeakPointContract("weak-1", "返回值", 3, 2L, 0.8f)
        ),
        tokenUsage = TokenUsageOverviewContract(
            totalTokens = 12_345L,
            estimatedTokens = 0L,
            isEstimated = false,
            period = "weekly"
        ),
        warnings = emptyList(),
        isNoData = false
    )
}

private class FakeLearningOverviewPort(
    private val overview: LearningOverviewContract
) : LearningOverviewPort {
    var loadError: Throwable? = null
    var lastLoadedScope: LearningOverviewScope? = null

    override suspend fun loadOverview(scope: LearningOverviewScope): LearningOverviewContract {
        lastLoadedScope = scope
        loadError?.let { throw it }
        return overview
    }
}
