package com.reversetutor.core.domain

/**
 * NEWMP-V1-002 Task 2.5A · Deterministic, model-free user-intent classifier.
 *
 * Turns one bounded user string into a whitelisted [UserIntent] so real chat
 * turns stop defaulting to [UserIntent.Ambiguous]. It is deliberately safe and
 * information-free:
 *
 *  - pure Kotlin; no Android, storage, LLM, Provider or clock dependency;
 *  - input is trimmed and only its first [MAX_SCAN_CHARS] characters are read;
 *  - the input text is never stored, logged or returned; the classifier emits
 *    only a category — never a keyword, match offset, confidence score or any
 *    original substring;
 *  - rules run in a fixed priority order and the first hit wins, so identical
 *    input always yields an identical [UserIntent];
 *  - anything not clearly a learning move falls back to
 *    [UserIntent.Ambiguous], which the selector resolves to a clarify/diagnose
 *    action rather than fabricating a learning fact.
 *
 * Scope: this class only *understands the current turn's intent*. It never
 * infers mastery, failure counts, evidence or learning-fact mutations — those
 * stay owned by the local verifier and ledger projector downstream.
 */
object GuidedLearningIntentClassifier {

    /** Only the leading window of the message is inspected; the rest is ignored. */
    const val MAX_SCAN_CHARS = 320

    fun classify(userText: String): UserIntent {
        // Bound the scan window and normalize case. The scratch value is local to
        // this call and is never retained, returned or logged.
        val text = userText.trim().take(MAX_SCAN_CHARS).lowercase()
        if (text.isBlank()) return UserIntent.Ambiguous

        // Priority 1 — explicit document/table tool operations.
        if (matchesAny(text, TOOL_MARKERS)) return UserIntent.ToolRequest
        // Priority 2 — explicit learning goal / subject / method / difficulty change.
        if (matchesAny(text, GOAL_CHANGE_MARKERS)) return UserIntent.GoalChange
        // Priority 3 — request for a hint or the next step.
        if (matchesAny(text, HINT_MARKERS)) return UserIntent.AskHint
        // Priority 4 — request for an example, worked case or counterexample.
        if (matchesAny(text, EXAMPLE_MARKERS)) return UserIntent.AskExample
        // Priority 5 — summarize / review / mastery check.
        if (matchesAny(text, REFLECT_MARKERS)) return UserIntent.Reflect
        // Priority 6 — the learner is producing an answer / derivation / code.
        if (looksLikeAnswerAttempt(text)) return UserIntent.AnswerAttempt
        // Priority 7 — a genuine question about the material.
        if (looksLikeQuestion(text)) return UserIntent.AskQuestion
        // Priority 8 — clearly non-learning small talk.
        if (looksLikeOffTopic(text)) return UserIntent.OffTopic
        // Priority 9 — everything else stays ambiguous (never guessed).
        return UserIntent.Ambiguous
    }

    // ---- Rule tables (fixed priority; keyword membership is order-independent) ----

    private val TOOL_MARKERS = listOf(
        "创建文档", "新建文档", "写文档", "写入文档", "生成文档",
        "新建表格", "整理表格", "生成表格", "创建表格", "做个表格", "建个表格",
        "导出文档", "存成文档"
    )

    private val GOAL_CHANGE_MARKERS = listOf(
        "改学", "改成", "换成", "换目标", "改目标", "更改目标", "换个目标",
        "换一种学习方式", "改变学习方式", "换个方式学", "换种学法",
        "提高难度", "加难度", "调高难度", "降低难度", "减难度", "调低难度",
        "改难度", "换难度", "换个方向", "改方向", "转换方向", "换学科"
    )

    private val HINT_MARKERS = listOf(
        "提示", "给点线索", "给点提示", "来点提示", "一点线索", "给点思路", "来点思路",
        "给点方向", "下一步", "没有头绪", "不知道从哪", "不知从哪", "卡住了", "卡住",
        "帮我想想", "引导一下", "不会", "看不懂", "不明白", "不懂"
    )

    private val EXAMPLE_MARKERS = listOf(
        "例题", "举个例子", "举一个例子", "举例子", "举几个例子", "例子里",
        "示例", "示范", "实例", "样例", "例子", "反例", "代码示例", "代码例子"
    )

    private val REFLECT_MARKERS = listOf(
        "总结", "概括", "复盘", "回顾", "梳理一下", "串一下", "检查一下",
        "掌握了吗", "掌握情况", "学会了吗", "会了吗", "记住了吗", "理解了吗",
        "讲讲我的理解"
    )

    private val ANSWER_LEAD_MARKERS = listOf(
        "我认为", "我觉得", "我的思路", "我的做法", "我的步骤", "我的答案是",
        "我算出", "我算得", "我推导", "我证明", "我验证", "我尝试", "我作答",
        "我回答", "我解得", "我的解法", "我的结论", "我的理解是",
        "答案是", "所以", "因为", "故", "解得", "得出", "由此可得", "经过计算", "计算得"
    )

    private val QUESTION_MARKERS = listOf(
        "什么是", "是什么", "为什么", "为啥", "怎么", "怎样", "咋", "如何",
        "能否解释", "能解释", "请解释", "解释一下", "解释下", "说明一下",
        "啥意思", "什么意思", "怎么办", "怎样办", "有什么", "有哪些", "是否",
        "能不能", "可不可以", "有没有"
    )

    private val OFF_TOPIC_MARKERS = listOf(
        "天气", "气温", "下雨", "晴天", "阴天", "几点了", "现在几点", "星期几",
        "今天几号", "吃了吗", "吃啥", "吃饭", "吃了没", "早上好", "中午好", "晚上好",
        "哈喽", "哈罗", "你好呀", "在干嘛", "在吗", "在不在", "无聊", "闲聊", "随便聊聊",
        "讲个笑话", "说个笑话", "推荐电影", "推荐电视剧", "明星", "八卦", "打游戏", "打球",
        "去旅游", "去哪里玩", "晚安", "早点休息", "看电影", "追剧", "综艺", "爱豆", "idol"
    )

    // ---- Composite detectors ----

    private fun matchesAny(text: String, markers: List<String>): Boolean =
        markers.any { text.contains(it.lowercase()) }

    private fun startsWithAny(text: String, markers: List<String>): Boolean =
        markers.any { text.startsWith(it.lowercase()) }

    /**
     * AnswerAttempt: an explicit "here is my answer/reasoning" opener, a leading
     * step label, a code snippet, or a concrete derivation result (a digit
     * preceded by an assignment/inequality operator). All signals are checked
     * only against the bounded local window; nothing is retained.
     */
    private fun looksLikeAnswerAttempt(text: String): Boolean {
        if (startsWithAny(text, ANSWER_LEAD_MARKERS)) return true
        if (text.contains("```") || text.contains("def ") || text.contains("=>") ||
            text.contains("system.out") || text.contains("print(")
        ) {
            return true
        }
        val digitAfterOperator = Regex("(?<==)\\s*-?\\d")
            .containsMatchIn(text) ||
            Regex("(?<=[<>])\\s*-?\\d").containsMatchIn(text) ||
            Regex("答案\\s*(是|为|等于)").containsMatchIn(text)
        return digitAfterOperator
    }

    private fun looksLikeQuestion(text: String): Boolean =
        text.trimEnd().endsWith("?") || text.trimEnd().endsWith("？") ||
            matchesAny(text, QUESTION_MARKERS)

    private fun looksLikeOffTopic(text: String): Boolean {
        if (matchesAny(text, OFF_TOPIC_MARKERS)) return true
        return text == "hi" || text == "hello" || text == "hey" || text == "你好" ||
            text == "在吗" || text == "哈哈" || text == "呵呵"
    }
}
