package com.reversetutor.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1 死循环干预检测器单测：三个信号的命中与不误报，灵敏度换挡，自动档升档。
 */
class ChatStagnationDetectorTest {

    private fun user(id: String, text: String) = ChatGuidanceMessage(id, false, text)
    private fun assistant(id: String, text: String) = ChatGuidanceMessage(id, true, text)

    @Test
    fun healthyConversationDoesNotTrigger() {
        val messages = listOf(
            assistant("a1", "你想先从哪一步开始讲呢？"),
            user("u1", "我们先做第一步：把日志统计脚本写出来，我来给你讲讲怎么写。"),
            assistant("a2", "太好了，那我们从统计 ERROR 开始。你在网页里问了什么？"),
            user("u2", "我问了 DeepSeek：请写一个统计日志里 ERROR 数量的脚本，它给了一段代码，我手工数了一遍，结果对得上。"),
            assistant("a3", "很好，记录表第一条就出来了！那循环是谁在跑？")
        )
        assertNull(ChatStagnationDetector.detect(messages, ChatGuidanceSensitivity.STANDARD))
    }

    @Test
    fun repeatedAssistantQuestionTriggers() {
        val q1 = "统计 ERROR 的脚本在网页版里怎么运行啊？你能先跑一遍给我看看吗？"
        val q2 = "统计 ERROR 的脚本在网页版里要怎么运行呢？你可以先跑一遍给我看看吗？"
        val messages = listOf(
            assistant("a1", q1),
            user("u1", "我不会啊，不知道咋运行"),
            assistant("a2", q2),
            user("u2", "写好脚本怎么搞，我不知道")
        )
        val signal = ChatStagnationDetector.detect(messages, ChatGuidanceSensitivity.STANDARD)
        assertNotNull(signal)
        assertTrue(ChatGuidanceReason.REPEATED_QUESTION in signal!!.reasons)
        assertTrue(ChatGuidanceReason.USER_FRUSTRATION in signal.reasons)
        assertEquals("u2", signal.triggerMessageId)
    }

    @Test
    fun distinctAssistantQuestionsDoNotTriggerRepeatedSignal() {
        val messages = listOf(
            assistant("a1", "你想先讲讲为什么需要终止条件吗？"),
            user("u1", "因为不终止会一直烧钱空转，这是我自己试出来的。"),
            assistant("a2", "那把它映射回实验里，Lab B 里它怎么知道该停了呢？"),
            user("u2", "我在记录表里写了：步数用完它会自己汇报然后退出。")
        )
        val signal = ChatStagnationDetector.detect(messages, ChatGuidanceSensitivity.STANDARD)
        if (signal != null) {
            assertFalse(ChatGuidanceReason.REPEATED_QUESTION in signal.reasons)
        }
    }

    @Test
    fun frustrationNeedsTwoHitsAtStandardButOneAtHigh() {
        val messages = listOf(
            assistant("a1", "我们继续吧，这一步你想从哪里开始讲？"),
            user("u1", "我不知道啊")
        )
        assertNull(ChatStagnationDetector.detect(messages, ChatGuidanceSensitivity.STANDARD))
        assertNotNull(ChatStagnationDetector.detect(messages, ChatGuidanceSensitivity.HIGH))
    }

    @Test
    fun longStallWithoutProgressTriggers() {
        val messages = listOf(
            assistant("a1", "第一步要做什么呀？"),
            user("u1", "不会"),
            user("u2", "不知道"),
            user("u3", "咋整"),
            user("u4", "咋运行"),
            user("u5", "该干嘛啊"),
            user("u6", "我不会啊")
        )
        val signal = ChatStagnationDetector.detect(messages, ChatGuidanceSensitivity.STANDARD)
        assertNotNull(signal)
        assertTrue(ChatGuidanceReason.NO_PROGRESS in signal!!.reasons)
        assertTrue(ChatGuidanceReason.USER_FRUSTRATION in signal.reasons)
    }

    @Test
    fun substantiveUserMessageResetsStallCounter() {
        val messages = listOf(
            assistant("a1", "第一步要做什么呀？"),
            user("u1", "不会"),
            user("u2", "不知道"),
            user("u3", "咋整"),
            user("u4", "先别急，我把任务原文贴给你：今天是实操日，先做后教，照 labs.md 做 Lab A，把手工环节逐条记进观察记录表。"),
            user("u5", "咋运行"),
            user("u6", "该干嘛啊")
        )
        val signal = ChatStagnationDetector.detect(messages, ChatGuidanceSensitivity.STANDARD)
        if (signal != null) {
            assertFalse(ChatGuidanceReason.NO_PROGRESS in signal.reasons)
        }
    }

    @Test
    fun sensitivityHighLowersSimilarityThreshold() {
        val q1 = "那这个实验要怎么开始？你打算先讲哪个环节的原理？"
        val q2 = "这个实验要怎么开始呀？你准备先讲哪个环节的原理？"
        val messages = listOf(
            assistant("a1", q1),
            user("u1", "我不会啊，不知道咋开始"),
            assistant("a2", q2)
        )
        assertNull(ChatStagnationDetector.detect(messages, ChatGuidanceSensitivity.LOW))
        assertNotNull(ChatStagnationDetector.detect(messages, ChatGuidanceSensitivity.HIGH))
    }

    @Test
    fun escalatedFlagPassesThroughToSignal() {
        val messages = listOf(
            assistant("a1", "咋运行啊？"),
            user("u1", "不会"),
            assistant("a2", "咋运行呢？"),
            user("u2", "不知道")
        )
        val signal = ChatStagnationDetector.detect(messages, ChatGuidanceSensitivity.STANDARD, escalated = true)
        assertNotNull(signal)
        assertTrue(signal!!.escalated)
    }

    @Test
    fun scaffoldTextChangesWithReason() {
        val repeated = ChatStagnationDetector.detect(
            listOf(
                assistant("a1", "咋运行啊？"),
                user("u1", "不会"),
                assistant("a2", "咋运行啊？"),
                user("u2", "不知道")
            ),
            ChatGuidanceSensitivity.STANDARD
        )!!
        assertTrue(ChatGuidanceContent.scaffoldText(repeated).contains("换个方式"))

        val stallOnly = listOf(
            assistant("a1", "第一步要做什么呀？"),
            user("u1", "不会"),
            user("u2", "不知道"),
            user("u3", "咋整"),
            user("u4", "咋运行"),
            user("u5", "该干嘛啊"),
            user("u6", "我不会啊")
        )
        val stall = ChatStagnationDetector.detect(stallOnly, ChatGuidanceSensitivity.LOW)!!
        assertTrue(ChatGuidanceContent.scaffoldText(stall).contains("先别再问我"))
    }

    @Test
    fun examplesMatchScriptRunCategory() {
        val signal = ChatGuidanceSignal(
            triggerMessageId = "u9",
            reasons = setOf(ChatGuidanceReason.USER_FRUSTRATION),
            escalated = true
        )
        val examples = ChatGuidanceContent.examples(signal, "脚本写好了咋运行，一直报错")
        assertTrue(examples.first().contains("执行一遍"))
        assertTrue(examples.any { it.contains("PowerShell") })
    }

    @Test
    fun modeFromLabelFallsBackToAuto() {
        assertEquals(ChatGuidanceMode.AUTO, ChatGuidanceMode.fromLabel(null))
        assertEquals(ChatGuidanceMode.AUTO, ChatGuidanceMode.fromLabel("不存在的档位"))
        assertEquals(ChatGuidanceMode.OFF, ChatGuidanceMode.fromLabel("关闭"))
        assertEquals(ChatGuidanceMode.HIGH, ChatGuidanceMode.fromLabel("高"))
    }
}
