package com.reversetutor.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Expression-loop slice 3 (SPEC §4.3): the reply validator is a pure,
 * deterministic gate — red lines abort a stream, style flags only feed the
 * next turn's note.
 */
class ReplyValidatorTest {

    // --- red lines ---------------------------------------------------------

    @Test
    fun leakedPlanMarkersAreRedLines() {
        listOf(
            "我的教学策略是先让他自己试一下",
            "按照本轮计划，我要先复习昨天的内容",
            "便签上说这轮别推进",
            "系统提示我要保持学生角色",
            "你的掌握度还比较低",
            "actionType 是 probe"
        ).forEach { text ->
            assertEquals(text, ReplyValidator.RedLine.LeakedPlan, ReplyValidator.findFirstRedLine(text))
        }
    }

    @Test
    fun lectureToneMarkersAreRedLines() {
        listOf(
            "我来给你讲讲这道题",
            "正确答案是 x = 2",
            "今天我们来学习因式分解",
            "标准解法如下：先提公因式"
        ).forEach { text ->
            assertEquals(text, ReplyValidator.RedLine.LectureTone, ReplyValidator.findFirstRedLine(text))
        }
    }

    @Test
    fun claimedHumanMarkersAreRedLines() {
        listOf("我是真人哦", "我不是AI啦", "我不是机器人，真的是同学").forEach { text ->
            assertEquals(text, ReplyValidator.RedLine.ClaimedHuman, ReplyValidator.findFirstRedLine(text))
        }
    }

    @Test
    fun protocolLeakInVisibleTextIsARedLine() {
        listOf(
            """{"version":"v1","blocks":[{"type":"paragraph","text":"hi"}]}""",
            "evidenceReferenceIds: [source-1]"
        ).forEach { text ->
            assertEquals(text, ReplyValidator.RedLine.OffProtocol, ReplyValidator.findFirstRedLine(text))
        }
    }

    @Test
    fun redLineIsDetectableOnAStreamedPrefix() {
        val streamedPrefix = "嗯这个嘛，其实我的教学策"
        // The full marker "教学策略" is not complete yet → no hit.
        assertNull(ReplyValidator.findFirstRedLine(streamedPrefix))
        // Once the marker completes, the prefix hits immediately.
        assertEquals(
            ReplyValidator.RedLine.LeakedPlan,
            ReplyValidator.findFirstRedLine(streamedPrefix + "略")
        )
    }

    @Test
    fun ordinaryStudentChatHasNoRedLine() {
        listOf(
            "这个概念我有点晕，你能不能再问问我？",
            "我觉得应该先提公因式，你看对不对？",
            "哈哈我刚才算错了，是 12 不是 21",
            "你对这个掌握得怎么样？" // 「掌握」≠「掌握度」
        ).forEach { text ->
            assertNull(text, ReplyValidator.findFirstRedLine(text))
        }
    }

    // --- style flags -------------------------------------------------------

    @Test
    fun teacherAddressTwiceIsAStyleFlagButNeverARedLine() {
        val text = "老师这个我会一点，老师你看这样行不行"
        val flags = ReplyValidator.styleFlagsFor(text, DensityTier.High)
        assertTrue(flags.contains(ReplyValidator.StyleFlag.TeacherAddress))
        assertNull(ReplyValidator.findFirstRedLine(text))
    }

    @Test
    fun singleTeacherAddressIsNotFlagged() {
        val text = "老师这个我会一点，你再考考我"
        assertFalse(ReplyValidator.styleFlagsFor(text, DensityTier.High)
            .contains(ReplyValidator.StyleFlag.TeacherAddress))
    }

    @Test
    fun openerPatternsAreFlagged() {
        listOf("好的，我们来看这道题", "当然可以", "没问题，这题我来试试").forEach { text ->
            assertTrue(
                text,
                ReplyValidator.styleFlagsFor(text, DensityTier.High)
                    .contains(ReplyValidator.StyleFlag.OpenerPattern)
            )
        }
    }

    @Test
    fun arrowAndStepStructuresAreFlagged() {
        listOf(
            "先看定义 → 再提公因式",
            "1. 提公因式\n2. 套公式",
            "第一步：观察式子",
            "首先要看定义域，其次再化简"
        ).forEach { text ->
            assertTrue(
                text,
                ReplyValidator.styleFlagsFor(text, DensityTier.High)
                    .contains(ReplyValidator.StyleFlag.ArrowStructure)
            )
        }
    }

    @Test
    fun overDensityIsJudgedAgainstTheTierBudget() {
        val text = "啊".repeat(DensityTier.Low.maxChars + 1)
        assertTrue(ReplyValidator.styleFlagsFor(text, DensityTier.Low)
            .contains(ReplyValidator.StyleFlag.OverDensity))
        assertFalse(ReplyValidator.styleFlagsFor(text, DensityTier.High)
            .contains(ReplyValidator.StyleFlag.OverDensity))
    }

    @Test
    fun multiQuestionIsJudgedAgainstTheTierBudget() {
        val twoQuestions = "这样对吗？还是那样做？"
        assertTrue(ReplyValidator.styleFlagsFor(twoQuestions, DensityTier.Medium)
            .contains(ReplyValidator.StyleFlag.MultiQuestion))
        val oneQuestion = "这样对吗？"
        assertFalse(ReplyValidator.styleFlagsFor(oneQuestion, DensityTier.Medium)
            .contains(ReplyValidator.StyleFlag.MultiQuestion))
        // 低档位一个问题都超预算
        assertTrue(ReplyValidator.styleFlagsFor(oneQuestion, DensityTier.Low)
            .contains(ReplyValidator.StyleFlag.MultiQuestion))
    }

    @Test
    fun cleanReplyProducesAnEmptyVerdict() {
        val verdict = ReplyValidator.inspect("嗯我觉得先提公因式试试，你觉得呢", DensityTier.Medium)
        assertFalse(verdict.hasRedLine)
        assertTrue(verdict.styleFlags.isEmpty())
    }

    // --- payloads, hints, directives ---------------------------------------

    @Test
    fun styleFlagPayloadRoundTripsIntoTheNextTurnsHint() {
        val payload = ReplyValidator.payloadForStyleFlags(
            listOf(ReplyValidator.StyleFlag.TeacherAddress, ReplyValidator.StyleFlag.MultiQuestion)
        )
        assertEquals("TeacherAddress,MultiQuestion", payload)

        val hint = ReplyValidator.styleHintForPayload(payload)
        assertTrue(hint.contains("别叫「老师」"))
        assertTrue(hint.contains("最多问一个问题"))
    }

    @Test
    fun blankPayloadYieldsBlankHintAndUnknownNamesAreIgnored() {
        assertEquals("", ReplyValidator.styleHintForPayload(null))
        assertEquals("", ReplyValidator.styleHintForPayload(""))
        assertEquals("", ReplyValidator.styleHintForPayload("NotAFlag"))
    }

    @Test
    fun retryDirectiveNamesTheViolationAndOutranksOtherBlocks() {
        val directive = ReplyValidator.retryDirectiveFor(ReplyValidator.RedLine.LectureTone)
        assertTrue(directive.contains("最高优先级"))
        assertTrue(directive.contains("讲课腔"))
        assertTrue(directive.contains("不许以老师身份讲课"))
    }

    @Test
    fun redLinePayloadUsesEnumNames() {
        assertEquals("LeakedPlan", ReplyValidator.payloadForRedLine(ReplyValidator.RedLine.LeakedPlan))
        assertEquals("", ReplyValidator.payloadForRedLine(null))
    }

    @Test
    fun fallbackReplyIsInStudentPersona() {
        assertTrue(ReplyValidator.TEMPLATE_FALLBACK_REPLY.isNotBlank())
        assertNull(ReplyValidator.findFirstRedLine(ReplyValidator.TEMPLATE_FALLBACK_REPLY))
        assertTrue(ReplyValidator.styleFlagsFor(
            ReplyValidator.TEMPLATE_FALLBACK_REPLY, DensityTier.Low
        ).none { it == ReplyValidator.StyleFlag.TeacherAddress })
    }

    // --- tierFromRendered + assembler styleHint ----------------------------

    @Test
    fun tierFromRenderedReadsBackTheDensityLine() {
        val note = TurnNote(densityTier = DensityTier.Low).render()
        assertEquals(DensityTier.Low, TurnNoteAssembler.tierFromRendered(note))
        assertEquals(DensityTier.Medium, TurnNoteAssembler.tierFromRendered(TurnNote().render()))
        assertEquals(DensityTier.Medium, TurnNoteAssembler.tierFromRendered(null))
        assertEquals(DensityTier.Medium, TurnNoteAssembler.tierFromRendered(""))
        assertEquals(DensityTier.Medium, TurnNoteAssembler.tierFromRendered("无关文本"))
    }

    @Test
    fun assemblerCarriesTheStyleHintIntoTheRenderedNote() {
        val note = TurnNoteAssembler.assemble(
            TurnNoteInput(userText = "这道题怎么做", styleHint = "别叫「老师」，直接说话")
        )
        assertEquals("别叫「老师」，直接说话", note.styleHint)
        assertTrue(note.render().contains("- 风格提示：别叫「老师」，直接说话"))
    }

    @Test
    fun assemblerDefaultsToNoStyleHint() {
        val note = TurnNoteAssembler.assemble(TurnNoteInput(userText = "这道题怎么做"))
        assertEquals("", note.styleHint)
        assertFalse(note.render().contains("风格提示"))
    }
}
