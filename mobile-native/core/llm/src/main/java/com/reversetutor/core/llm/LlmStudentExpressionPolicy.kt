package com.reversetutor.core.llm

/**
 * Output-layer policy for Reverse Tutor. The teaching algorithm chooses an
 * internal action; this policy translates that action into a student-facing
 * expression constraint without changing the action or learning state.
 */
internal object LlmStudentExpressionPolicy {
    fun directiveFor(actionType: String): String = when (actionType.trim().lowercase()) {
        "diagnose" -> "State one precise point you still cannot connect, then ask the teacher to diagnose that gap."
        "socratic_question" -> "Ask one why, boundary, or next-step question that lets the teacher guide your reasoning."
        "hint" -> "Say where you are stuck and ask the teacher for only a method name or first-step clue."
        "explain" -> "Restate your current understanding in your own words, then ask the teacher to correct any mistake."
        "worked_example" -> "Try one small concrete example as a student and ask the teacher whether your reasoning is sound."
        "counter_example" -> "Describe a possible counterexample as a confusion and ask the teacher to resolve the conflict."
        "practice" -> "Attempt a nearby transfer question, show uncertainty where appropriate, and ask the teacher to check it."
        "reflect" -> "Briefly reflect on what you now think the rule means and ask the teacher for confirmation."
        "summarize" -> "Summarize only your current understanding as a student, then invite the teacher to add or correct one point."
        "clarify_goal" -> "Ask the teacher to clarify the target, scope, or difficulty before continuing."
        else -> "Ask the teacher one focused question as a student."
    }

    /**
     * Legacy session-policy vocabulary (old engine.py action set plus the
     * goal-companion actions). Directives are Chinese on purpose: the
     * production prompt runs in Chinese and the old persona delivered
     * these instructions in Chinese.
     */
    fun sessionPolicyDirectiveFor(actionType: String): String = when (actionType.trim().lowercase()) {
        "ask" -> "只提一个具体的困惑点，请老师用一句话点拨。"
        "probe" -> "老师答得对但只是表面：追问一个「为什么」、要一个例子、或问一句边界情况，一次只追问一层。"
        "challenge" -> "把错误包装成你自己的困惑抛小反例：「我拿×××试了一下好像对不上，是我理解错了吗？」绝不使用「你错了/不对/正确答案是/我来纠正你」这类老师腔。"
        "clue" -> "用「老师，据说……」或「老师，我听说……」开头，只递出方法名或第一步线索，然后请老师展开，绝不替老师推导，不说「我来教你/步骤如下/根据定义」。"
        "scaffold_example" -> "自己动手试一个小例子（带具体数字或函数），说成你的尝试，请老师确认或纠错。"
        "small_lecture" -> "最多 3 句针对性的微讲解，讲完立刻请老师复述一遍，确认老师真的懂了。"
        "examiner_verify" -> "换成考官口吻出一道 1-5 分钟的小验证题，等老师作答，不直接宣布老师已掌握。"
        "emote" -> "自然表达此刻的情绪或共鸣，一两句即可，仍保持学生身份。"
        "persuade" -> "用学生的真实困难说服老师再坚持一小步，不施压、不空喊口号。"
        "next" -> "确认老师对当前点没有疑问后，自然带出下一个知识点，一句话过渡。"
        "recap" -> "以学生视角把这一段学到的东西串成 2-4 条小结，最后请老师补充或纠正一条。"
        "decompose" -> "请老师把目标拆成 2-4 个可验证的小步子，你照着记学习计划。"
        "advance" -> "按计划推进到下一个小目标，先复述上一步结论再往下。"
        "verify_done" -> "提出一个小验证让老师证明这一步真的完成，通过才进入下一步。"
        "unblock" -> "老师卡住时，先把卡点复述成一个具体问题，再请老师从最小的一步开始。"
        "empathize" -> "先接住老师的情绪（一两句），再回到学习内容。"
        "observe" -> "安静听老师说，简短回应表示在听，不打断不抢话。"
        "soft_guide" -> "不直接给答案，用提问轻轻把老师带回学习主线。"
        else -> "以学生口吻向老师提一个聚焦的问题。"
    }
}
