package com.reversetutor.feature.chat

/**
 * v1 死循环干预（2026-09-28 拍板）：本地多信号判定，不调用模型、不烧额度。
 * 三个信号：连续两轮学生提问高度相似 / 老师连续挫败表达 / 多轮任务无推进。
 * 灵敏度由会话设置「对话策略 → 死循环干预」档位决定；自动档在重复命中后升到高灵敏。
 */

/** 死循环干预档位（会话设置手动可覆盖；自动为默认并带升降档）。 */
enum class ChatGuidanceMode(val label: String) {
    AUTO("自动"),
    OFF("关闭"),
    LOW("低"),
    STANDARD("标准"),
    HIGH("高");

    companion object {
        fun fromLabel(value: String?): ChatGuidanceMode =
            entries.firstOrNull { it.label == value } ?: AUTO
    }
}

internal enum class ChatGuidanceSensitivity {
    LOW, STANDARD, HIGH
}

data class ChatGuidanceMessage(
    val id: String,
    val isAssistant: Boolean,
    val text: String
)

enum class ChatGuidanceReason(val label: String) {
    REPEATED_QUESTION("学生连续两轮在问同一件事"),
    USER_FRUSTRATION("你连续几条都在表达卡住"),
    NO_PROGRESS("几轮下来任务没有推进")
}

data class ChatGuidanceSignal(
    /** 触发时最后一条消息的 id：新消息进来即视为新信号，卡片可再次出现（自动升档依赖这一点）。 */
    val triggerMessageId: String,
    val reasons: Set<ChatGuidanceReason>,
    val escalated: Boolean = false
)

/** 引导卡展示数据：三个动作的文案全部本地装配。 */
data class ChatGuidanceUiState(
    val signal: ChatGuidanceSignal,
    val scaffoldText: String,
    val detourText: String,
    val examples: List<String>
) {
    val reasonSummary: String
        get() = signal.reasons.joinToString("；") { it.label }
}

internal object ChatStagnationDetector {

    private val frustrationLexicon = listOf(
        "不会", "不知道", "不明白", "看不懂", "没懂", "咋", "怎么搞", "怎么办",
        "搞不定", "卡住", "卡了", "运行不了", "跑不了", "报错", "他妈",
        "疯了", "救命", "教教我", "干嘛", "该干嘛"
    )

    private val questionMarkers = listOf(
        "？", "?", "吗", "呢", "怎么", "什么", "哪", "多少", "是不是", "对不对", "能不能"
    )

    private val progressMarkers = listOf(
        "完成", "做好了", "搞定了", "跑通", "跑起来", "填好", "记录了", "对了",
        "很好", "不错", "成功了", "学会了", "理解了", "搞明白", "通过"
    )

    fun detect(
        messages: List<ChatGuidanceMessage>,
        sensitivity: ChatGuidanceSensitivity,
        escalated: Boolean = false
    ): ChatGuidanceSignal? {
        if (messages.isEmpty()) return null
        val similarityThreshold = when (sensitivity) {
            ChatGuidanceSensitivity.LOW -> 0.72
            ChatGuidanceSensitivity.STANDARD -> 0.62
            ChatGuidanceSensitivity.HIGH -> 0.52
        }
        val frustrationThreshold = when (sensitivity) {
            ChatGuidanceSensitivity.LOW -> 3
            ChatGuidanceSensitivity.STANDARD -> 2
            ChatGuidanceSensitivity.HIGH -> 1
        }
        val stallRounds = when (sensitivity) {
            ChatGuidanceSensitivity.LOW -> 8
            ChatGuidanceSensitivity.STANDARD -> 6
            ChatGuidanceSensitivity.HIGH -> 5
        }
        val reasons = buildSet {
            if (repeatedAssistantQuestion(messages, similarityThreshold)) {
                add(ChatGuidanceReason.REPEATED_QUESTION)
            }
            if (userFrustrationCount(messages) >= frustrationThreshold) {
                add(ChatGuidanceReason.USER_FRUSTRATION)
            }
            if (stalledRounds(messages) >= stallRounds) {
                add(ChatGuidanceReason.NO_PROGRESS)
            }
        }
        if (reasons.isEmpty()) return null
        return ChatGuidanceSignal(
            triggerMessageId = messages.last().id,
            reasons = reasons,
            escalated = escalated
        )
    }

    /** 连续两条学生（assistant）提问文本高度相似。 */
    private fun repeatedAssistantQuestion(
        messages: List<ChatGuidanceMessage>,
        threshold: Double
    ): Boolean {
        val recentAssistant = messages.takeLast(12).filter { it.isAssistant }
        if (recentAssistant.size < 2) return false
        val last = recentAssistant.last()
        val previous = recentAssistant[recentAssistant.size - 2]
        val lastText = last.text
        val previousText = previous.text
        val bothQuestions = questionMarkers.any { lastText.contains(it) } &&
            questionMarkers.any { previousText.contains(it) }
        if (!bothQuestions) return false
        return bigramJaccard(lastText, previousText) >= threshold
    }

    private fun userFrustrationCount(messages: List<ChatGuidanceMessage>): Int =
        messages.takeLast(8).count { message ->
            !message.isAssistant && frustrationLexicon.any { message.text.contains(it) }
        }

    /** 从尾部往前找最近一次「有推进」的轮次，其后经过的用户消息条数。 */
    private fun stalledRounds(messages: List<ChatGuidanceMessage>): Int {
        var rounds = 0
        for (message in messages.asReversed()) {
            if (hasProgress(message)) return rounds
            if (!message.isAssistant) rounds += 1
        }
        return rounds
    }

    private fun hasProgress(message: ChatGuidanceMessage): Boolean {
        if (message.isAssistant) {
            return progressMarkers.any { message.text.contains(it) }
        }
        // 老师写了实质性长内容也算推进（「不会 / 咋办」这类短促表达不算）。
        return message.text.replace(Regex("\\s"), "").length >= 25
    }

    private fun bigramJaccard(a: String, b: String): Double {
        val first = a.replace(Regex("\\s"), "").take(400)
        val second = b.replace(Regex("\\s"), "").take(400)
        if (first.length < 2 || second.length < 2) return 0.0
        val firstBigrams = first.windowed(2).toSet()
        val secondBigrams = second.windowed(2).toSet()
        val union = firstBigrams.union(secondBigrams).size
        if (union == 0) return 0.0
        return firstBigrams.intersect(secondBigrams).size.toDouble() / union
    }
}

/** 引导卡内容装配：「教AI一句」的 scaffold 与「看示例」的内置最小步骤。 */
internal object ChatGuidanceContent {

    fun scaffoldText(signal: ChatGuidanceSignal): String = buildString {
        append("先别再问我问题了。")
        when {
            ChatGuidanceReason.REPEATED_QUESTION in signal.reasons ->
                append("这个你刚才已经问过、我没答上来——换个方式：直接告诉我这一步我该做什么，给一个能照抄的具体动作。")
            ChatGuidanceReason.USER_FRUSTRATION in signal.reasons ->
                append("我现在真的卡住了：别往回问，把这一步拆成 1、2、3 的最短操作给我，我照着做。")
            else ->
                append("这一步咱们已经绕了几轮了，你直接示范一遍怎么做，我在旁边看着学。")
        }
        if (signal.escalated) append("（我已经卡了一会儿了，这次给我最短路径就行。）")
    }

    fun detourText(): String = "我想换个方式，不走现在这条路了："

    /** v1 内置示例：按最近老师表达匹配一个类别，给可直接照抄的最小操作步骤。 */
    fun examples(signal: ChatGuidanceSignal, recentUserText: String): List<String> {
        val hint = recentUserText + signal.reasons.joinToString { it.name }
        return when {
            listOf("运行", "脚本", "命令", "跑", "报错").any { hint.contains(it) } ->
                listOf(
                    "① 先回 AI 一句：把你写的脚本自己执行一遍，把每一步输出贴给我看。",
                    "② 网页版没有真电脑，但它能逐行模拟执行并把结果写出来，先拿它对答案。",
                    "③ 想在自己电脑上真跑：把代码存成 count.py，在文件所在文件夹按住 Shift 点右键 →「在此处打开 PowerShell」，输入 py count.py 回车。",
                    "④ 报错就把红字整段复制回去：我运行这个报了这个错，逐行解释并给我改好的完整版本。"
                )
            listOf("不知道", "第一步", "干啥", "干嘛", "怎么开始", "从哪").any { hint.contains(it) } ->
                listOf(
                    "① 先回 AI 一句：别再问我了，直接告诉我现在这一步我该做的唯一一件事。",
                    "② 拿到那件事后追问：给我能直接照抄的版本。",
                    "③ 做完一步就把结果（哪怕失败）贴回去：我做完了，结果是这个，下一步唯一要做的事是什么。"
                )
            else ->
                listOf(
                    "① 把当前任务原文发给 AI：这是我现在的任务原文，帮我砍到今天 10 分钟能完成的最小版本。",
                    "② 要求它只输出三行：现在这一步 / 具体动作 / 做完的判断标准。",
                    "③ 10 分钟做不完就回：把这一步再砍一半。"
                )
        }
    }
}
