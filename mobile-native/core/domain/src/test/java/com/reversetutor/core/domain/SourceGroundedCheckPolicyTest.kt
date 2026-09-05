package com.reversetutor.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * NEWMP-V1-002 plan Task 2 · [SourceGroundedCheckPolicy] rules.
 *
 * A check plan is only usable when it is source-grounded: a non-blank
 * revision, every handle whitelisted by the current context, bounded text and
 * no secret-like content. Anything the local rules cannot decide comes back as
 * [CheckVerification.Unverified] — never as a learning failure.
 */
class SourceGroundedCheckPolicyTest {

    private fun plan(
        sourceRevision: String = "rev-src-1-1000",
        sourceHandles: List<String> = listOf("source:src-1:rev-src-1-1000"),
        prompt: String = "一元二次方程判别式等于0意味着什么？",
        expectedAnswer: String = "有一个重根",
        rule: CheckRule = CheckRule.RequiredConcepts(listOf("重根", "判别式"))
    ) = SourceGroundedCheckPlan(
        id = "check-1",
        sourceRevision = sourceRevision,
        sourceHandles = sourceHandles,
        prompt = prompt,
        expectedAnswer = expectedAnswer,
        rule = rule,
        conceptKey = "判别式"
    )

    private val whitelist = setOf("source:src-1:rev-src-1-1000")

    // ---- 白名单与版本门槛 ----

    @Test
    fun blankSourceRevisionIsRejected() {
        assertNull(SourceGroundedCheckPolicy.normalize(plan(sourceRevision = "  "), whitelist))
    }

    @Test
    fun handleOutsideCurrentContextWhitelistIsRejected() {
        assertNull(
            SourceGroundedCheckPolicy.normalize(
                plan(sourceHandles = listOf("source:src-1:rev-src-1-1000", "source:ghost:rev-9")),
                whitelist
            )
        )
    }

    @Test
    fun emptyHandleListIsRejected() {
        assertNull(SourceGroundedCheckPolicy.normalize(plan(sourceHandles = emptyList()), whitelist))
    }

    @Test
    fun groundedPlanNormalizesDeterministically() {
        val once = SourceGroundedCheckPolicy.normalize(plan(prompt = "  带空白的题目  "), whitelist)
        val twice = SourceGroundedCheckPolicy.normalize(plan(prompt = "  带空白的题目  "), whitelist)
        assertNotNull(once)
        assertEquals(once, twice)
        assertEquals("带空白的题目", once?.prompt)
    }

    // ---- 限长与敏感片段 ----

    @Test
    fun oversizedFieldsAreBounded() {
        val huge = "长".repeat(500)
        val normalized = SourceGroundedCheckPolicy.normalize(
            plan(prompt = huge, expectedAnswer = huge, rule = CheckRule.Rubric(List(30) { huge })),
            whitelist
        )
        assertNotNull(normalized)
        assertTrue(normalized!!.prompt.length <= SourceGroundedCheckPolicy.PROMPT_MAX)
        assertTrue(normalized.expectedAnswer.length <= SourceGroundedCheckPolicy.EXPECTED_ANSWER_MAX)
        val rubric = normalized.rule as CheckRule.Rubric
        assertTrue(rubric.criteria.size <= SourceGroundedCheckPolicy.MAX_RUBRIC_CRITERIA)
        rubric.criteria.forEach { assertTrue(it.length <= SourceGroundedCheckPolicy.RUBRIC_ITEM_MAX) }
    }

    @Test
    fun secretLikeFragmentsInPlanAreRejected() {
        for (needle in listOf(
            "see https://api.invalid/v1",
            "Authorization: Bearer sk-abcdef123456",
            "key sk-abcdef123456 needed"
        )) {
            assertNull(
                "含敏感片段的计划必须整体拒绝: $needle",
                SourceGroundedCheckPolicy.normalize(plan(prompt = "题目$needle"), whitelist)
            )
            assertNull(
                SourceGroundedCheckPolicy.normalize(
                    plan(rule = CheckRule.RequiredConcepts(listOf("概念A", needle))), whitelist
                )
            )
        }
    }

    // ---- 本地验证语义 ----

    @Test
    fun versionMismatchIsUnverifiedNotFailed() {
        val grounded = SourceGroundedCheckPolicy.normalize(plan(), whitelist)!!
        assertEquals(
            CheckVerification.Unverified,
            SourceGroundedCheckPolicy.validateAnswer(grounded, "有一个重根，判别式为零", currentSourceRevision = "rev-src-1-2000")
        )
    }

    @Test
    fun rubricIsNeverDecidedLocally() {
        val grounded = SourceGroundedCheckPolicy.normalize(
            plan(rule = CheckRule.Rubric(listOf("解释完整", "给出边界条件"))), whitelist
        )!!
        assertEquals(
            CheckVerification.Unverified,
            SourceGroundedCheckPolicy.validateAnswer(grounded, "自认为说清楚了", currentSourceRevision = grounded.sourceRevision)
        )
    }

    @Test
    fun exactTextPassesOnlyOnNormalizedEquality() {
        val grounded = SourceGroundedCheckPolicy.normalize(
            plan(expectedAnswer = "有一个重根", rule = CheckRule.ExactText("有一个重根")), whitelist
        )!!
        val rev = grounded.sourceRevision
        assertEquals(CheckVerification.VerifiedPassed, SourceGroundedCheckPolicy.validateAnswer(grounded, " 有一个重根 ", rev))
        assertEquals(CheckVerification.VerifiedFailed, SourceGroundedCheckPolicy.validateAnswer(grounded, "有两个根", rev))
    }

    @Test
    fun numericToleranceBoundaryIsInclusive() {
        val grounded = SourceGroundedCheckPolicy.normalize(
            plan(expectedAnswer = "4", rule = CheckRule.NumericTolerance(expected = 4.0, tolerance = 0.5)), whitelist
        )!!
        val rev = grounded.sourceRevision
        assertEquals(CheckVerification.VerifiedPassed, SourceGroundedCheckPolicy.validateAnswer(grounded, "4.5", rev))
        assertEquals(CheckVerification.VerifiedFailed, SourceGroundedCheckPolicy.validateAnswer(grounded, "4.51", rev))
        assertEquals(CheckVerification.VerifiedFailed, SourceGroundedCheckPolicy.validateAnswer(grounded, "大概四左右吧", rev))
    }

    @Test
    fun requiredConceptsMapToPassPartialFail() {
        val grounded = SourceGroundedCheckPolicy.normalize(
            plan(rule = CheckRule.RequiredConcepts(listOf("重根", "判别式"))), whitelist
        )!!
        val rev = grounded.sourceRevision
        assertEquals(
            CheckVerification.VerifiedPassed,
            SourceGroundedCheckPolicy.validateAnswer(grounded, "判别式为0时方程有重根", rev)
        )
        assertEquals(
            CheckVerification.VerifiedPartial,
            SourceGroundedCheckPolicy.validateAnswer(grounded, "这里还有一个判别式的问题", rev)
        )
        assertEquals(
            CheckVerification.VerifiedFailed,
            SourceGroundedCheckPolicy.validateAnswer(grounded, "我不知道怎么算", rev)
        )
    }
}
