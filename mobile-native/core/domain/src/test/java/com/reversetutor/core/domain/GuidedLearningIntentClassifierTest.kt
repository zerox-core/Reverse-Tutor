package com.reversetutor.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * NEWMP-V1-002 Task 2.5A · rule table for [GuidedLearningIntentClassifier].
 *
 * Written with the classifier but not executed in this task (no Gradle run is
 * allowed for T2.5A); it is the pinned acceptance for the next regression pass.
 * Every assertion checks only the returned category — the classifier must
 * never expose the input text, keywords or offsets.
 */
class GuidedLearningIntentClassifierTest {

    // ---- fixed priority chains ----

    @Test
    fun toolRequestWinsOverOtherMarkers() {
        assertEquals(UserIntent.ToolRequest, GuidedLearningIntentClassifier.classify("帮我创建文档并总结"))
        assertEquals(UserIntent.ToolRequest, GuidedLearningIntentClassifier.classify("把这些整理成表格并新建表格"))
    }

    @Test
    fun goalChangeWinsOverHintStyleWording() {
        assertEquals(UserIntent.GoalChange, GuidedLearningIntentClassifier.classify("我想改成大学数学"))
        assertEquals(UserIntent.GoalChange, GuidedLearningIntentClassifier.classify("换成物理吧，这个提示先不管"))
        assertEquals(UserIntent.GoalChange, GuidedLearningIntentClassifier.classify("提高难度，我觉得太简单"))
    }

    @Test
    fun askHintDetected() {
        assertEquals(UserIntent.AskHint, GuidedLearningIntentClassifier.classify("给我一点提示"))
        assertEquals(UserIntent.AskHint, GuidedLearningIntentClassifier.classify("这题不会，下一步怎么入手"))
        assertEquals(UserIntent.AskHint, GuidedLearningIntentClassifier.classify("给点思路"))
    }

    @Test
    fun askExampleDetected() {
        assertEquals(UserIntent.AskExample, GuidedLearningIntentClassifier.classify("给一个反例"))
        assertEquals(UserIntent.AskExample, GuidedLearningIntentClassifier.classify("能举个例子吗"))
        assertEquals(UserIntent.AskExample, GuidedLearningIntentClassifier.classify("来一道例题"))
    }

    @Test
    fun reflectDetected() {
        assertEquals(UserIntent.Reflect, GuidedLearningIntentClassifier.classify("帮我总结一下今天的内容"))
        assertEquals(UserIntent.Reflect, GuidedLearningIntentClassifier.classify("复盘一下刚才的错题"))
        assertEquals(UserIntent.Reflect, GuidedLearningIntentClassifier.classify("我学会了吗，检查掌握情况"))
    }

    @Test
    fun answerAttemptDetected() {
        assertEquals(UserIntent.AnswerAttempt, GuidedLearningIntentClassifier.classify("我认为答案是单调递增"))
        assertEquals(UserIntent.AnswerAttempt, GuidedLearningIntentClassifier.classify("所以先求导再令其为0"))
        assertEquals(UserIntent.AnswerAttempt, GuidedLearningIntentClassifier.classify("f(x)=2x+1，代入得 3"))
        assertEquals(UserIntent.AnswerAttempt, GuidedLearningIntentClassifier.classify("def solve(n):\n    return n*2"))
    }

    @Test
    fun askQuestionDetected() {
        assertEquals(UserIntent.AskQuestion, GuidedLearningIntentClassifier.classify("什么是函数的单调性？"))
        assertEquals(UserIntent.AskQuestion, GuidedLearningIntentClassifier.classify("为什么要先检验定义域"))
        assertEquals(UserIntent.AskQuestion, GuidedLearningIntentClassifier.classify("如何判断极值点"))
    }

    @Test
    fun offTopicDetected() {
        assertEquals(UserIntent.OffTopic, GuidedLearningIntentClassifier.classify("今天天气不错，适合出去玩"))
        assertEquals(UserIntent.OffTopic, GuidedLearningIntentClassifier.classify("你好呀"))
        assertEquals(UserIntent.OffTopic, GuidedLearningIntentClassifier.classify("给我讲个笑话吧"))
    }

    @Test
    fun unknownRequestsStayAmbiguous() {
        assertEquals(UserIntent.Ambiguous, GuidedLearningIntentClassifier.classify(""))
        assertEquals(UserIntent.Ambiguous, GuidedLearningIntentClassifier.classify("   "))
        assertEquals(UserIntent.Ambiguous, GuidedLearningIntentClassifier.classify("嗯嗯"))
        assertEquals(UserIntent.Ambiguous, GuidedLearningIntentClassifier.classify("这里写一段完全不含任何规则关键词的叙述文本"))
    }

    // ---- safety properties ----

    @Test
    fun emptyAndBlankNeverMatchLearningCategories() {
        assertEquals(UserIntent.Ambiguous, GuidedLearningIntentClassifier.classify("\n\t  "))
    }

    @Test
    fun onlyLeadingWindowIsScanned() {
        val longPrefix = "a".repeat(GuidedLearningIntentClassifier.MAX_SCAN_CHARS)
        val beyondWindow = longPrefix + "帮我创建一个文档"
        // 关键词被截在 320 字符窗口之外 → 不命中工具意图
        assertEquals(UserIntent.Ambiguous, GuidedLearningIntentClassifier.classify(beyondWindow))
        assertEquals(UserIntent.ToolRequest, GuidedLearningIntentClassifier.classify("帮我创建文档" + longPrefix.take(400)))
    }

    @Test
    fun classificationIsDeterministicForSameInput() {
        val samples = listOf("给我一点提示", "我想改成大学数学", "什么是单调性？", "今天天气好吗", "我认为答案是B")
        samples.forEach { sample ->
            // same input ⇒ same category (no clock/random/model dependence)
            val first = GuidedLearningIntentClassifier.classify(sample)
            val second = GuidedLearningIntentClassifier.classify(sample)
            org.junit.Assert.assertSame(sample, first, second)
        }
    }

    @Test
    fun crossSubjectAndStyleChangeAreNotOffTopic() {
        // 跨学科/改难度/改风格是 GoalChange（优先级 2），不得识别为 OffTopic
        assertNotAmbiguousNorOffTopic("我们改学英语吧")
        assertNotAmbiguousNorOffTopic("换成表格形式讲，别用文字")
    }

    private fun assertNotAmbiguousNorOffTopic(text: String) {
        val intent = GuidedLearningIntentClassifier.classify(text)
        assertFalse("跨学科/风格变化不得落入 OffTopic: $text", intent == UserIntent.OffTopic)
        assertFalse("跨学科/风格变化不应是 Ambiguous: $text", intent == UserIntent.Ambiguous)
    }
}
