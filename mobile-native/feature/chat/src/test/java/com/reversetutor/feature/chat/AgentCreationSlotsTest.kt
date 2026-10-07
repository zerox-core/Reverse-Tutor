package com.reversetutor.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** R102：确认闸门槽位状态机 + 确认信号判定（纯函数，零额度）。 */
class AgentCreationSlotsTest {

    // ---------- 槽位状态机 ----------

    @Test
    fun proposeWritesEmptyAndOverwritesProposed() {
        var slots = CreationSlots()
        slots = slots.propose(CreationSlot.Goal, "学魔方", 1)
        assertEquals(SlotStatus.Proposed, slots.statusOf(CreationSlot.Goal))
        assertEquals("学魔方", slots.valueOf(CreationSlot.Goal))
        // 提案可被新提案覆盖（同处于未定稿状态）
        slots = slots.propose(CreationSlot.Goal, "学三阶魔方", 2)
        assertEquals("学三阶魔方", slots.valueOf(CreationSlot.Goal))
        assertEquals(2, slots.entries[CreationSlot.Goal]?.round)
    }

    @Test
    fun proposeNeverOverwritesConfirmed() {
        var slots = CreationSlots().confirm(CreationSlot.Goal, "把浮力讲明白", 1)
        slots = slots.propose(CreationSlot.Goal, "模型想改成学物理", 2)
        assertEquals(SlotStatus.Confirmed, slots.statusOf(CreationSlot.Goal))
        assertEquals("把浮力讲明白", slots.valueOf(CreationSlot.Goal))
        assertEquals(1, slots.entries[CreationSlot.Goal]?.round)
    }

    @Test
    fun proposeBlankValueIsNoop() {
        val slots = CreationSlots().propose(CreationSlot.Goal, "  ", 1)
        assertEquals(SlotStatus.Empty, slots.statusOf(CreationSlot.Goal))
    }

    @Test
    fun confirmWithEmptyValueTakesProposal() {
        var slots = CreationSlots().propose(CreationSlot.Persona, "慢热但较真", 1)
        slots = slots.confirm(CreationSlot.Persona, round = 2)
        assertEquals(SlotStatus.Confirmed, slots.statusOf(CreationSlot.Persona))
        assertEquals("慢热但较真", slots.valueOf(CreationSlot.Persona))
        assertEquals(2, slots.entries[CreationSlot.Persona]?.round)
    }

    @Test
    fun confirmWithoutAnyValueIsNoop() {
        val slots = CreationSlots().confirm(CreationSlot.Goal, round = 1)
        assertEquals(SlotStatus.Empty, slots.statusOf(CreationSlot.Goal))
    }

    @Test
    fun requiredAndOptionalJudgement() {
        var slots = CreationSlots()
        assertFalse(slots.requiredConfirmed())
        assertFalse(slots.optionalAllConfirmed())
        slots = slots.confirm(CreationSlot.Goal, "学魔方", 1)
        assertFalse(slots.requiredConfirmed())
        slots = slots.confirm(CreationSlot.LearnerRole, "零基础", 2)
        assertTrue(slots.requiredConfirmed())
        assertFalse(slots.optionalAllConfirmed())
        slots = slots.confirm(CreationSlot.Persona, "慢热", 3)
            .confirm(CreationSlot.TeachingStyle, "多举例", 3)
            .confirm(CreationSlot.Constraints, "每天 20 分钟", 3)
        assertTrue(slots.optionalAllConfirmed())
    }

    @Test
    fun autoConfirmProposedOnlyLiftsProposed() {
        val slots = CreationSlots()
            .propose(CreationSlot.Goal, "学魔方", 1)
            .confirm(CreationSlot.LearnerRole, "零基础", 1)
        val lifted = slots.autoConfirmProposed(9)
        assertEquals(SlotStatus.Confirmed, lifted.statusOf(CreationSlot.Goal))
        assertEquals(9, lifted.entries[CreationSlot.Goal]?.round)
        // 已 Confirmed 的轮次不被改写
        assertEquals(1, lifted.entries[CreationSlot.LearnerRole]?.round)
        assertEquals(SlotStatus.Empty, lifted.statusOf(CreationSlot.Persona))
    }

    @Test
    fun resetClearsSlot() {
        val slots = CreationSlots().confirm(CreationSlot.Goal, "学魔方", 1).reset(CreationSlot.Goal)
        assertEquals(SlotStatus.Empty, slots.statusOf(CreationSlot.Goal))
        assertEquals("", slots.valueOf(CreationSlot.Goal))
    }

    @Test
    fun migratedFromDowngradesDraftFieldsToProposed() {
        val draft = NewSessionConfiguration(
            goal = "把浮力讲明白",
            learnerRole = "初二学生",
            persona = "慢热",
            dialogueStrategy = "多举例",
            plan = "每天 20 分钟",
            learningScope = "力学",
            stageMilestones = "概念→例题"
        )
        val slots = CreationSlots.migratedFrom(draft)
        assertEquals(SlotStatus.Proposed, slots.statusOf(CreationSlot.Goal))
        assertEquals(SlotStatus.Proposed, slots.statusOf(CreationSlot.LearnerRole))
        assertEquals(SlotStatus.Proposed, slots.statusOf(CreationSlot.Persona))
        assertEquals(SlotStatus.Proposed, slots.statusOf(CreationSlot.TeachingStyle))
        assertEquals(SlotStatus.Proposed, slots.statusOf(CreationSlot.Constraints))
        assertEquals("把浮力讲明白", slots.valueOf(CreationSlot.Goal))
        assertTrue(slots.valueOf(CreationSlot.Constraints).contains("每天 20 分钟"))
        // 旧数据一律降 Proposed——必填判定不通过，必须用户确认一次
        assertFalse(slots.requiredConfirmed())
    }

    @Test
    fun namedEntriesRoundTrip() {
        val slots = CreationSlots()
            .confirm(CreationSlot.Goal, "学魔方", 3)
            .propose(CreationSlot.Persona, "慢热", 2)
        val restored = CreationSlots.fromNamedEntries(slots.toNamedEntries())
        assertEquals(slots, restored)
    }

    @Test
    fun fromNamedEntriesDropsUnknownNames() {
        val restored = CreationSlots.fromNamedEntries(
            mapOf(
                "Goal" to SlotEntry(SlotStatus.Confirmed, "学魔方", 1),
                "NotASlot" to SlotEntry(SlotStatus.Proposed, "x", 1)
            )
        )
        assertEquals(1, restored.entries.size)
        assertEquals(SlotStatus.Confirmed, restored.statusOf(CreationSlot.Goal))
    }

    @Test
    fun slotFromLabelAlignsWithPlannerFields() {
        assertEquals(CreationSlot.Goal, CreationSlot.fromLabel("学习目标"))
        assertEquals(CreationSlot.LearnerRole, CreationSlot.fromLabel("学习者角色"))
        assertEquals(CreationSlot.Persona, CreationSlot.fromLabel("人物性格"))
        assertNull(CreationSlot.fromLabel("不存在的字段"))
        assertNull(CreationSlot.fromLabel(null))
    }

    // ---------- 确认信号 ----------

    @Test
    fun explicitConfirmMatchesShortAffirmatives() {
        assertTrue(CreationConfirmationSignals.isExplicitConfirm("对"))
        assertTrue(CreationConfirmationSignals.isExplicitConfirm("好的"))
        assertTrue(CreationConfirmationSignals.isExplicitConfirm("可以！"))
        assertTrue(CreationConfirmationSignals.isExplicitConfirm("嗯"))
        assertTrue(CreationConfirmationSignals.isExplicitConfirm("就这个"))
        assertFalse(CreationConfirmationSignals.isExplicitConfirm("对，但我想改成二阶"))
        assertFalse(CreationConfirmationSignals.isExplicitConfirm("好像不太对"))
    }

    @Test
    fun confirmPrefixRequiresNoNegation() {
        assertTrue(CreationConfirmationSignals.hasConfirmPrefix("对，就学三阶"))
        assertTrue(CreationConfirmationSignals.hasConfirmPrefix("好，就这个方向"))
        assertFalse(CreationConfirmationSignals.hasConfirmPrefix("对，但是我更想学二阶"))
        assertFalse(CreationConfirmationSignals.hasConfirmPrefix("好像可以吧，不过我还不确定"))
    }

    @Test
    fun nonAnswerInterceptsQuestions() {
        // 事故场景：反问一律不得确认悬置提案
        assertTrue(CreationConfirmationSignals.isNonAnswer("我该学什么呀？"))
        assertTrue(CreationConfirmationSignals.isNonAnswer("为什么推荐三阶？"))
        assertTrue(CreationConfirmationSignals.isNonAnswer("哪个更适合我"))
        assertFalse(CreationConfirmationSignals.isNonAnswer("对"))
        assertFalse(CreationConfirmationSignals.isNonAnswer("我想学三阶魔方"))
        assertFalse(CreationConfirmationSignals.isNonAnswer(""))
    }

    @Test
    fun overlapsProposalDetectsAdoption() {
        assertTrue(CreationConfirmationSignals.overlapsProposal("学会三阶魔方基础还原", "就学三阶魔方基础还原吧"))
        assertTrue(CreationConfirmationSignals.overlapsProposal("三阶魔方", "我想学三阶魔方"))
        // 提案包含用户短答也算（用户复述了提案关键词）
        assertTrue(CreationConfirmationSignals.overlapsProposal("学会三阶魔方基础还原", "三阶魔方"))
        assertFalse(CreationConfirmationSignals.overlapsProposal("学会三阶魔方基础还原", "我想学编程"))
        assertFalse(CreationConfirmationSignals.overlapsProposal("", "随便"))
        assertFalse(CreationConfirmationSignals.overlapsProposal("提案", ""))
    }

    @Test
    fun userSourcedIsOverlapBetweenValueAndUserText() {
        assertTrue(CreationConfirmationSignals.userSourced("初二学生", "我是初二学生"))
        assertFalse(CreationConfirmationSignals.userSourced("初二学生", "第一句"))
        // 单字值永不判 userSourced（防止误确认）
        assertFalse(CreationConfirmationSignals.userSourced("r", "我要期末冲刺"))
    }
}
