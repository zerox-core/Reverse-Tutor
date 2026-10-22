package com.reversetutor.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentCreationParserTest {

    @Test
    fun parsesPlainJsonTurnResult() {
        val raw = """
            {"understanding":42,"followUpQuestion":"你的目标是什么？","requestDocument":false,
             "draft":{"title":"浮力·讲学练会话","learnerRole":"初二学生"}}
        """.trimIndent()

        val result = AgentCreationParser.parseTurnResult(raw)

        assertNotNull(result)
        result!!
        assertEquals(42, result.understanding)
        assertEquals("你的目标是什么？", result.followUpQuestion)
        assertNull(result.assistantNote)
        assertFalse(result.requestDocument)
        assertEquals("浮力·讲学练会话", result.draft?.title)
        assertEquals("初二学生", result.draft?.learnerRole)
        assertNull(result.draft?.goal)
    }

    @Test
    fun parsesPersonaIntoDraftPatch() {
        val raw = """{"understanding":50,"followUpQuestion":"他什么性格？","draft":{"persona":"慢热但较真"}}"""

        val result = AgentCreationParser.parseTurnResult(raw)

        assertEquals("慢热但较真", result?.draft?.persona)
    }

    // 2026-10-16 用户拍板：模型自选工具。action 是模型输出的工具名，解析要宽松、缺省要安全。
    @Test
    fun parsesToolChoiceAction() {
        assertEquals(
            AgentCreationAction.DirectCreate,
            AgentCreationParser.parseTurnResult("""{"action":"direct_create","assistantNote":"建会话"}""")?.action
        )
        assertEquals(
            AgentCreationAction.Interview,
            AgentCreationParser.parseTurnResult("""{"action":"interview","assistantNote":"再聊聊"}""")?.action
        )
        // 容忍大小写 / 连字符 / 同义词
        assertEquals(
            AgentCreationAction.DirectCreate,
            AgentCreationParser.parseTurnResult("""{"action":" Create-Now ","assistantNote":"好"}""")?.action
        )
        // tool / toolName 是同一个意思的别名
        assertEquals(
            AgentCreationAction.Interview,
            AgentCreationParser.parseTurnResult("""{"tool":"multi_turn","assistantNote":"问一句"}""")?.action
        )
    }

    @Test
    fun unparsableActionDegradesToNull() {
        // 不认识的词 → null（协调器按 interview 保守处理，绝不擅自创建）
        val unknown = AgentCreationParser.parseTurnResult("""{"action":"make_coffee","assistantNote":"嗯"}""")
        assertNull(unknown?.action)
        assertNotNull(unknown)

        // 没报 action 的旧格式照旧能解析
        val legacy = AgentCreationParser.parseTurnResult("""{"understanding":30,"assistantNote":"记下了"}""")
        assertNull(legacy?.action)
        assertEquals(30, legacy?.understanding)
    }

    @Test
    fun parsesFencedJsonWithSurroundingProse() {
        val raw = "好的，我理解了。\n```json\n" +
            "{\"understanding\":70,\"assistantNote\":\"草案已更新。\",\"followUpQuestion\":\"先讲概念还是先做题？\"," +
            "\"requestDocument\":true,\"draft\":{\"goal\":\"期末冲刺\"}}\n" +
            "```\n以上是本轮结果。"

        val result = AgentCreationParser.parseTurnResult(raw)

        assertNotNull(result)
        result!!
        assertEquals(70, result.understanding)
        assertEquals("草案已更新。", result.assistantNote)
        assertEquals("先讲概念还是先做题？", result.followUpQuestion)
        assertTrue(result.requestDocument)
        assertEquals("期末冲刺", result.draft?.goal)
    }

    @Test
    fun understandingIsClampedToZeroToHundred() {
        val tooHigh = AgentCreationParser.parseTurnResult(
            """{"understanding":9999,"draft":{"title":"t","learnerRole":"r"}}"""
        )
        val tooLow = AgentCreationParser.parseTurnResult(
            """{"understanding":-40,"draft":{"title":"t","learnerRole":"r"}}"""
        )

        assertEquals(100, tooHigh?.understanding)
        assertEquals(0, tooLow?.understanding)
    }

    @Test
    fun invalidOrEmptyPayloadReturnsNull() {
        assertNull(AgentCreationParser.parseTurnResult("这不是 JSON。"))
        assertNull(AgentCreationParser.parseTurnResult(""))
        assertNull(AgentCreationParser.parseTurnResult("{\"understanding\":50}"))
        assertNull(AgentCreationParser.parseTurnResult("{broken"))
    }

    @Test
    fun draftPatchKeepsOnlyFieldsActuallyPresent() {
        val raw = """
            {"understanding":55,"followUpQuestion":"聊聊基础？",
             "draft":{"plan":"三步走：概念、例题、复盘","feedbackIntensity":4}}
        """.trimIndent()

        val draft = AgentCreationParser.parseTurnResult(raw)!!.draft!!

        assertEquals("三步走：概念、例题、复盘", draft.plan)
        assertEquals(4, draft.feedbackIntensity)
        assertNull(draft.title)
        assertNull(draft.probingIntensity)

        val base = NewSessionConfiguration(title = "保留旧标题", learnerRole = "旧角色", plan = "旧计划")
        val merged = draft.applyTo(base)

        assertEquals("保留旧标题", merged.title)
        assertEquals("旧角色", merged.learnerRole)
        assertEquals("三步走：概念、例题、复盘", merged.plan)
        assertEquals(4, merged.feedbackIntensity)
    }

    @Test
    fun intensityOutsideRangeIsDropped() {
        val raw = """{"understanding":50,"followUpQuestion":"?","draft":{"scaffoldingIntensity":9}}"""

        assertNull(AgentCreationParser.parseTurnResult(raw)!!.draft!!.scaffoldingIntensity)
    }

    @Test
    fun parsesDocAnalysisWithDefaults() {
        val raw = """
            前置说明文字
            {"materialTitle":"八年级物理下册","outline":["浮力","阿基米德原理"],"difficulty":0.8,
             "knowledgePoints":["浮力大小","排水体积"],"prerequisites":["密度"],
             "suggestedPath":["浮力 → 阿基米德原理 → 综合题"]}
            后置说明文字
        """.trimIndent()

        val analysis = AgentCreationParser.parseDocAnalysis(raw)

        assertNotNull(analysis)
        analysis!!
        assertEquals("八年级物理下册", analysis.materialTitle)
        assertEquals("其他", analysis.materialType)
        assertEquals(listOf("浮力", "阿基米德原理"), analysis.outline)
        assertEquals(0.8f, analysis.difficulty)
        assertEquals(listOf("密度"), analysis.prerequisites)
        assertEquals(1, analysis.suggestedPath.size)
        assertEquals("", analysis.summary)
    }

    @Test
    fun docAnalysisWithoutTitleReturnsNull() {
        assertNull(AgentCreationParser.parseDocAnalysis("""{"outline":["x"]}"""))
        assertNull(AgentCreationParser.parseDocAnalysis("完全不是 JSON"))
    }

    @Test
    fun textListsAreTrimmedCappedAndFiltered() {
        val raw = """
            {"materialTitle":"讲义","outline":["  第一章  ","","第二章"],
             "knowledgePoints":[${List(40) { "\"点$it\"" }.joinToString(",") { it }}]}
        """.trimIndent()

        val analysis = AgentCreationParser.parseDocAnalysis(raw)
        assertNotNull("parseDocAnalysis=null, raw=[" + raw + "]", analysis)
        analysis!!

        assertEquals(listOf("第一章", "第二章"), analysis.outline)
        assertEquals(30, analysis.knowledgePoints.size)
    }

    // R93：流式宽容抽取——契约 JSON 没生成完时，边到边抽口语字段上屏。
    @Test
    fun extractPartialSpokenReadsIncompleteStringPrefix() {
        val partial = """{"understanding":55,"assistantNote":"记下了，草案"""
        assertEquals("记下了，草案", AgentCreationParser.extractPartialSpoken(partial))
    }

    @Test
    fun extractPartialSpokenJoinsFieldsInJsonOrder() {
        // followUp 已闭合 + note 增长中：按 JSON 出现顺序换行拼接
        val partial = """{"understanding":55,"followUpQuestion":"目标是什么？","assistantNote":"记下"""
        assertEquals("目标是什么？\n记下", AgentCreationParser.extractPartialSpoken(partial))
    }

    @Test
    fun extractPartialSpokenUnescapesSequences() {
        val partial = """{"assistantNote":"第一行\n第二行\"引"""
        assertEquals("第一行\n第二行\"引", AgentCreationParser.extractPartialSpoken(partial))
    }

    @Test
    fun extractPartialSpokenParsesCompletePayload() {
        val full = """{"understanding":55,"followUpQuestion":"目标是什么？","assistantNote":"记下了。"}"""
        assertEquals("目标是什么？\n记下了。", AgentCreationParser.extractPartialSpoken(full))
    }

    @Test
    fun extractPartialSpokenReturnsNullBeforeSpokenFieldsAppear() {
        assertNull(AgentCreationParser.extractPartialSpoken(""))
        assertNull(AgentCreationParser.extractPartialSpoken("""{"understanding"""))
        assertNull(AgentCreationParser.extractPartialSpoken("""{"understanding":55,"draft":{"title":"浮力"""))
        // 冒号 / 开引号还没来：不上屏
        assertNull(AgentCreationParser.extractPartialSpoken("""{"assistantNote"""))
        assertNull(AgentCreationParser.extractPartialSpoken("""{"assistantNote":"""))
    }
}
