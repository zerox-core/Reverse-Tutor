package com.reversetutor.feature.reversetutor.card

import com.reversetutor.feature.reversetutor.session.RTSession
import org.json.JSONArray
import org.json.JSONObject

/**
 * 学生角色卡（规格源：spike app/engine/student_card.py）。
 *
 * 「酒馆式」分层：文采/性格是不可计算的品味，不走工具——
 * 用自然语言卡（身份/性格/口吻 + 分桶示例台词）驱动 LLM 的表达层；
 * 事实层照旧由工具守卫。三张卡：proud_average / curious_talker / quiet_slow_burn。
 *
 * 数据来自 assets/student_cards.json（内容与代码分离，改台词不改代码）。
 */
class StudentCards(cardsJson: String) {

    data class ExampleLine(val line: String)

    data class Card(
        val cardId: String,
        val name: String,
        val identityText: String,
        val personalityText: String,
        val speechStyle: String,
        val catchphrases: List<String>,
        val exampleBuckets: Map<String, List<ExampleLine>>
    )

    val cards: Map<String, Card>

    init {
        val root = JSONObject(cardsJson)
        val map = LinkedHashMap<String, Card>()
        for (cid in root.keys()) {
            val c = root.getJSONObject(cid)
            val buckets = LinkedHashMap<String, List<ExampleLine>>()
            val eb = c.getJSONObject("example_buckets")
            for (b in eb.keys()) {
                val arr = eb.getJSONArray(b)
                buckets[b] = (0 until arr.length()).map { i ->
                    ExampleLine(arr.getJSONObject(i).getString("line"))
                }
            }
            map[cid] = Card(
                cardId = c.getString("card_id"),
                name = c.getString("name"),
                identityText = c.getString("identity_text"),
                personalityText = c.getString("personality_text"),
                speechStyle = c.getString("speech_style"),
                catchphrases = stringList(c, "catchphrases"),
                exampleBuckets = buckets
            )
        }
        cards = map
    }

    fun getCard(cardId: String?): Card? = cardId?.let { cards[it] }

    companion object {
        private var defaultCardsInstance: StudentCards? = null

        fun setDefault(instance: StudentCards) {
            defaultCardsInstance = instance
        }

        fun getCard(cardId: String?): Card? {
            if (cardId == null) return null
            return defaultCardsInstance?.getCard(cardId)
        }

        /** 全部桶名（8 桶结构）。 */
        val BUCKET_NAMES = listOf(
            "confused_low", "confused_blindspot", "facade_cracking", "breakthrough",
            "confident_challenging", "embarrassed_deflect", "pressured_by_authority",
            "wrapping_up"
        )

        /**
         * 纯函数：会话状态 → 注入哪些示例桶（主桶在前）。
         *
         * 状态维度：情绪事件（最近顿悟/挣扎）、装懂被拆穿信号、水平与盲区组合。
         * 这是「状态触发」的 lorebook 对应物：角色随真状态换声线。
         * 工具信号源 = session.toolTrace（entry 带 round）。
         */
        fun selectBuckets(session: RTSession): List<String> {
            val buckets = mutableListOf<String>()
            val level = session.understandingLevel
            val cur = session.currentRound
            // 本轮工具调用发生在提示词渲染之后，所以取上轮及更早（round >= cur-1）
            val toolsUsed: Set<String> = session.toolTrace
                .filter { it.round >= cur - 1 }
                .map { it.tool }
                .toSet()

            // 1) 最近有顿悟事件（round 窗口内）→ 兴奋优先；emotion 常滞后不作硬条件
            val recentBreakthrough = session.breakthroughEvents.any { it.round >= cur - 1 }
            if (recentBreakthrough) buckets.add("breakthrough")
            // 2) 装懂被拆穿是事件事实：make_deliberate_mistake + check_understanding
            if ("make_deliberate_mistake" in toolsUsed && "check_understanding" in toolsUsed) {
                buckets.add("facade_cracking")
            }
            // 3) 高水平 + 有困惑概念 → 盲区困惑声线
            if (level >= 4 && session.confusedConcepts.isNotEmpty()) {
                buckets.add("confused_blindspot")
            }
            // 4) 低水平 → 基础困惑
            if (level <= 2) buckets.add("confused_low")
            // 5) 老师错误/权威压力信号
            if ("design_counterexample" in toolsUsed || "detect_misconception" in toolsUsed) {
                buckets.add("pressured_by_authority")
            }
            // 6) 自信学生默认声线（未被以上状态命中时）
            if (buckets.isEmpty()) buckets.add("confident_challenging")

            // 收尾信号：LLM 上轮调了 plan_session_closure → 告别声线优先占位
            val closing = session.currentRound >= 1 && "plan_session_closure" in toolsUsed
            return if (closing) {
                (listOf("wrapping_up") + buckets.filter { it != "wrapping_up" }).take(3)
            } else {
                buckets.take(3)
            }
        }

        /** 渲染角色卡提示词段：静态人格 + 按状态选中的示例台词。 */
        fun renderCardPrompt(card: Card, session: RTSession): String {
            val lines = mutableListOf(
                "【你的角色卡：${card.name}】（风格参考——学口吻，内容必须贴当前对话事实，禁止照抄台词）",
                "- 重要：角色性格优先于礼貌默认倾向——要面子就表现出来，嘴硬就嘴硬，不要主动变乖；",
                "- 身份：${card.identityText}",
                "- 性格：${card.personalityText}",
                "- 说话风格：${card.speechStyle}"
            )
            if (card.catchphrases.isNotEmpty()) {
                lines.add("- 口癖（偶尔用，别每句都用）：${card.catchphrases.joinToString("、")}")
            }
            for (b in selectBuckets(session)) {
                val examples = card.exampleBuckets[b] ?: continue
                lines.add("- 当前声线参考[$b]：")
                for (e in examples.take(3)) {
                    lines.add("  · ${e.line}")
                }
            }
            return lines.joinToString("\n")
        }

        private fun stringList(obj: JSONObject, key: String): List<String> {
            val arr = obj.optJSONArray(key) ?: return emptyList()
            return (0 until arr.length()).mapNotNull { arr.optString(it, null) }
        }

        private fun stringList(arr: JSONArray): List<String> =
            (0 until arr.length()).mapNotNull { arr.optString(it, null) }
    }
}
