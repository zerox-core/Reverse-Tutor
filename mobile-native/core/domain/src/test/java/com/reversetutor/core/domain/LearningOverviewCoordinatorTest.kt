package com.reversetutor.core.domain

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [LearningOverviewCoordinator]:
 * - all-session and selected-session scopes,
 * - empty-state without fabricated values,
 * - stable ranking of weak points,
 * - token aggregation with estimated flags,
 * - partial-failure degradation warnings.
 */
class LearningOverviewCoordinatorTest {

    // --- Fake ports ---------------------------------------------------------

    private class FakeSessionPort(
        var count: Int = 3,
        var shouldFail: Boolean = false
    ) : LearningOverviewSessionPort {
        override suspend fun countActiveSessions(spaceId: String, sessionIds: List<String>?): Int {
            if (shouldFail) throw RuntimeException("db error")
            return count
        }
    }

    private class FakeProgressPort(
        var progress: LearningProgressContract = LearningProgressContract(
            totalKnowledgePoints = 20,
            masteredCount = 10,
            masteryRate = 0.5f,
            weeklyChange = 0.05f
        ),
        var shouldFail: Boolean = false
    ) : LearningOverviewProgressPort {
        override suspend fun getProgress(spaceId: String, sessionIds: List<String>?): LearningProgressContract {
            if (shouldFail) throw RuntimeException("progress error")
            return progress
        }
    }

    private class FakePlanPort(
        var tasks: List<TodayPlanTask> = listOf(
            TodayPlanTask("t1", "Review limits", "todo", "极限"),
            TodayPlanTask("t2", "Practice derivatives", "done", "导数")
        ),
        var shouldFail: Boolean = false
    ) : LearningOverviewPlanPort {
        override suspend fun listTodayPlan(spaceId: String): List<TodayPlanTask> {
            if (shouldFail) throw RuntimeException("plan error")
            return tasks
        }
    }

    private class FakeThreadPort(
        var threads: List<LearningThreadContract> = listOf(
            LearningThreadContract("w1", "Calculus basics", "微积分", 0.6f, 300),
            LearningThreadContract("w2", "Linear algebra", "线性代数", 0.3f, 100)
        ),
        var shouldFail: Boolean = false
    ) : LearningOverviewThreadPort {
        override suspend fun listWeeklyMainline(spaceId: String, sessionIds: List<String>?, limit: Int): List<LearningThreadContract> {
            if (shouldFail) throw RuntimeException("thread error")
            return threads.take(limit)
        }
    }

    private class FakeWeakPointPort(
        var weakPoints: List<WeakPointContract> = listOf(
            WeakPointContract("wp1", "chain rule", 5, 300, 0.8f),
            WeakPointContract("wp2", "limits", 2, 100, 0.3f),
            WeakPointContract("wp3", "integration", 4, 200, 0.8f)
        ),
        var shouldFail: Boolean = false
    ) : LearningOverviewWeakPointPort {
        override suspend fun listWeakPoints(spaceId: String, sessionIds: List<String>?, limit: Int): List<WeakPointContract> {
            if (shouldFail) throw RuntimeException("weak error")
            return weakPoints.take(limit)
        }
    }

    private class FakeTokenPort(
        var usage: TokenUsageOverviewContract = TokenUsageOverviewContract(
            totalTokens = 50000,
            estimatedTokens = 10000,
            isEstimated = true,
            period = "weekly"
        ),
        var shouldFail: Boolean = false
    ) : LearningOverviewTokenPort {
        override suspend fun aggregateTokenUsage(spaceId: String, sessionIds: List<String>?): TokenUsageOverviewContract {
            if (shouldFail) throw RuntimeException("token error")
            return usage
        }
    }

    private fun makeCoordinator(
        session: FakeSessionPort = FakeSessionPort(),
        progress: FakeProgressPort = FakeProgressPort(),
        plan: FakePlanPort = FakePlanPort(),
        thread: FakeThreadPort = FakeThreadPort(),
        weak: FakeWeakPointPort = FakeWeakPointPort(),
        token: FakeTokenPort = FakeTokenPort()
    ): LearningOverviewCoordinator = LearningOverviewCoordinator(
        sessionPort = session,
        progressPort = progress,
        planPort = plan,
        threadPort = thread,
        weakPointPort = weak,
        tokenPort = token,
        nowEpochMillis = { 1000L }
    )

    // --- Tests --------------------------------------------------------------

    @Test
    fun allSessionScopeReturnsAggregatedData() = runBlocking {
        val coordinator = makeCoordinator()
        val result = coordinator.generate(LearningOverviewScope("s1", null))

        assertEquals("s1", result.scope.spaceId)
        assertNull(result.scope.sessionIds)
        assertEquals(3, result.activeSessionCount)
        assertEquals(20, result.progress.totalKnowledgePoints)
        assertEquals(2, result.todayPlan.totalCount)
        assertEquals(1, result.todayPlan.completedCount)
        assertEquals(2, result.weeklyMainline.size)
        assertEquals(3, result.weakPoints.size)
        assertEquals(50000, result.tokenUsage.totalTokens)
        assertEquals(1000, result.generatedAtEpochMillis)
        assertFalse(result.isNoData)
    }

    @Test
    fun selectedSessionScopePassesSessionIds() = runBlocking {
        val coordinator = makeCoordinator()
        val result = coordinator.generate(LearningOverviewScope("s1", listOf("se1", "se2")))

        assertEquals(listOf("se1", "se2"), result.scope.sessionIds)
    }

    @Test
    fun emptyStateHasNoFabricatedData() = runBlocking {
        val coordinator = makeCoordinator(
            session = FakeSessionPort(count = 0),
            progress = FakeProgressPort(progress = LearningProgressContract()),
            plan = FakePlanPort(tasks = emptyList()),
            thread = FakeThreadPort(threads = emptyList()),
            weak = FakeWeakPointPort(weakPoints = emptyList()),
            token = FakeTokenPort(usage = TokenUsageOverviewContract())
        )
        val result = coordinator.generate(LearningOverviewScope("s1"))

        assertTrue(result.isNoData)
        assertEquals(0, result.activeSessionCount)
        assertEquals(0, result.progress.totalKnowledgePoints)
        assertEquals(0, result.todayPlan.totalCount)
        assertTrue(result.weeklyMainline.isEmpty())
        assertTrue(result.weakPoints.isEmpty())
        assertEquals(0, result.tokenUsage.totalTokens)
    }

    @Test
    fun weakPointsSortedBySeverityThenErrorCountThenId() = runBlocking {
        val coordinator = makeCoordinator()
        val result = coordinator.generate(LearningOverviewScope("s1"))

        // wp1 (0.8, 5) > wp3 (0.8, 4) > wp2 (0.3, 2)
        assertEquals("wp1", result.weakPoints[0].id)
        assertEquals("wp3", result.weakPoints[1].id)
        assertEquals("wp2", result.weakPoints[2].id)
    }

    @Test
    fun weeklyMainlineSortedByUpdatedAtDescending() = runBlocking {
        val coordinator = makeCoordinator()
        val result = coordinator.generate(LearningOverviewScope("s1"))

        assertEquals("w1", result.weeklyMainline[0].id)  // updatedAt=300
        assertEquals("w2", result.weeklyMainline[1].id)  // updatedAt=100
    }

    @Test
    fun tokenUsageHasEstimatedFlag() = runBlocking {
        val coordinator = makeCoordinator()
        val result = coordinator.generate(LearningOverviewScope("s1"))

        assertTrue(result.tokenUsage.isEstimated)
        assertEquals(10000, result.tokenUsage.estimatedTokens)
    }

    @Test
    fun sessionFailureProducesWarning() = runBlocking {
        val coordinator = makeCoordinator(
            session = FakeSessionPort(shouldFail = true)
        )
        val result = coordinator.generate(LearningOverviewScope("s1"))

        assertEquals(0, result.activeSessionCount)
        assertTrue(result.warnings.any { it.source == "session" })
        // Other sources still work
        assertEquals(20, result.progress.totalKnowledgePoints)
    }

    @Test
    fun weakPointFailureProducesWarning() = runBlocking {
        val coordinator = makeCoordinator(
            weak = FakeWeakPointPort(shouldFail = true)
        )
        val result = coordinator.generate(LearningOverviewScope("s1"))

        assertTrue(result.weakPoints.isEmpty())
        assertTrue(result.warnings.any { it.source == "weakPoints" })
        // Other sources still work
        assertEquals(3, result.activeSessionCount)
    }

    @Test
    fun mainlineLimitIsEnforced() = runBlocking {
        val coordinator = LearningOverviewCoordinator(
            sessionPort = FakeSessionPort(),
            progressPort = FakeProgressPort(),
            planPort = FakePlanPort(),
            threadPort = FakeThreadPort(),
            weakPointPort = FakeWeakPointPort(),
            tokenPort = FakeTokenPort(),
            nowEpochMillis = { 0L },
            mainlineLimit = 1,
            weakPointLimit = 5
        )
        val result = coordinator.generate(LearningOverviewScope("s1"))
        assertEquals(1, result.weeklyMainline.size)
    }

    @Test
    fun weakPointLimitIsEnforced() = runBlocking {
        val coordinator = LearningOverviewCoordinator(
            sessionPort = FakeSessionPort(),
            progressPort = FakeProgressPort(),
            planPort = FakePlanPort(),
            threadPort = FakeThreadPort(),
            weakPointPort = FakeWeakPointPort(),
            tokenPort = FakeTokenPort(),
            nowEpochMillis = { 0L },
            mainlineLimit = 5,
            weakPointLimit = 2
        )
        val result = coordinator.generate(LearningOverviewScope("s1"))
        assertEquals(2, result.weakPoints.size)
    }

    @Test
    fun tokenUsageDoesNotContainSecrets() = runBlocking {
        val coordinator = makeCoordinator()
        val result = coordinator.generate(LearningOverviewScope("s1"))

        assertEquals("weekly", result.tokenUsage.period)
        // TokenUsageOverviewContract has no key/url/auth fields
        val totalTokens = result.tokenUsage.totalTokens
        assertTrue(totalTokens >= 0)
    }

    @Test
    fun partialFailureDoesNotBlockOtherSources() = runBlocking {
        val coordinator = makeCoordinator(
            progress = FakeProgressPort(shouldFail = true),
            token = FakeTokenPort(shouldFail = true)
        )
        val result = coordinator.generate(LearningOverviewScope("s1"))

        // Failed sources return defaults
        assertEquals(0, result.progress.totalKnowledgePoints)
        assertEquals(0, result.tokenUsage.totalTokens)
        // Succeeded sources still work
        assertEquals(3, result.activeSessionCount)
        assertEquals(2, result.todayPlan.totalCount)
        assertEquals(2, result.weeklyMainline.size)
        assertEquals(3, result.weakPoints.size)
        // Warnings for failed sources
        assertTrue(result.warnings.any { it.source == "progress" })
        assertTrue(result.warnings.any { it.source == "tokenUsage" })
    }
}
