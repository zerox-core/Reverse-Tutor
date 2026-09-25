package com.reversetutor.preview.shell

import com.reversetutor.core.domain.ActivitySummary
import com.reversetutor.core.domain.ActivityTask
import com.reversetutor.feature.chat.NewSessionConfiguration
import com.reversetutor.feature.chat.canonicalActivitySource
import com.reversetutor.feature.chat.normalizeActivitySource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChallengeSessionLaunchTest {
    @Test
    fun activitySourcesNormalizeToOneCanonicalIdentity() {
        assertEquals("activity:python-21", canonicalActivitySource(" Python-21 "))
        assertEquals("activity:python-21", normalizeActivitySource(" ACTIVITY : Python-21 "))
        assertEquals(null, normalizeActivitySource("source:python-21"))
        assertEquals(null, normalizeActivitySource("activity:  "))
    }

    @Test
    fun newestExistingSessionWithCanonicalActivitySourceIsReused() {
        val decision = resolveChallengeSessionLaunch(
            activity(),
            listOf(
                candidate("old", 10L, "activity:python-21"),
                candidate("new", 20L, " ACTIVITY : PYTHON-21 "),
                candidate("other", 30L, "activity:other")
            )
        )

        assertEquals("new", (decision as ChallengeSessionLaunchDecision.Reuse).candidate.sessionId)
    }

    @Test
    fun missingSessionProducesValidPrefillWithOnlyCanonicalActivitySource() {
        val decision = resolveChallengeSessionLaunch(activity(), emptyList())
            as ChallengeSessionLaunchDecision.Create

        assertEquals(listOf("activity:python-21"), decision.prefill.configuration.sourceSelections)
        assertTrue(decision.prefill.configuration.validationErrors().isEmpty())
        assertTrue(decision.prefill.configuration.goal.contains("21 天"))
    }

    @Test
    fun currentDayTaskIsInjectedIntoPrefill() {
        val decision = resolveChallengeSessionLaunch(
            activity(),
            emptyList(),
            ActivityTask(
                dayNumber = 3L,
                title = "设计数据模型",
                taskMarkdown = "- 画出表结构\n- 写迁移脚本",
                stageGoal = "完成数据层设计"
            )
        ) as ChallengeSessionLaunchDecision.Create

        val config = decision.prefill.configuration
        assertTrue(config.goal.contains("完成数据层设计"))
        assertTrue(config.goal.contains("总目标"))
        assertEquals("- 画出表结构\n- 写迁移脚本", config.plan)
        assertTrue(config.openingMessage.contains("第 3 天"))
        assertTrue(config.openingMessage.contains("设计数据模型"))
    }

    private fun activity() = ActivitySummary(
        id = "Python-21",
        title = "Python 挑战",
        revision = 7L,
        description = "完成 21 天学习目标",
        state = "active"
    )

    private fun candidate(id: String, updatedAt: Long, source: String) =
        ChallengeSessionCandidate(
            sessionId = id,
            title = id,
            learnerRole = "学习伙伴",
            updatedAtEpochMillis = updatedAt,
            snapshot = NewSessionConfiguration(sourceSelections = listOf(source))
        )
}
