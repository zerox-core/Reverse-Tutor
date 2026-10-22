package com.reversetutor.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 行内公式定界符识别（2026-10-15 用户反馈「没有 latex 渲染」的集成补丁）。
 *
 * `$...$` 是模型主用法、既有行为；`\(...\)` 与 `\[...\]` 是部分模型的写法，
 * 之前不认就会被当成普通文本整段显示——带反斜杠的 LaTeX 源码直接外露。
 * 这里锁定三种写法都能进公式渲染链路，且渲染结果里不残留反斜杠。
 */
class ChatInlineMathDelimiterTest {

    private fun formulas(source: String): List<String> =
        ChatRichContentParser.parseInlines(source)
            .filterIsInstance<ChatRichInline.Formula>()
            .map { it.source }

    @Test
    fun recognizesParenthesizedInlineMath() {
        assertEquals(listOf("x^2"), formulas("看 \\(x^2\\) 这里"))
    }

    @Test
    fun recognizesBracketedInlineMath() {
        assertEquals(listOf("\\frac{a}{b}"), formulas("结果 \\[\\frac{a}{b}\\] 如下"))
    }

    @Test
    fun dollarInlineMathStillWorksAlongsideNewDelimiters() {
        assertEquals(listOf("x^2", "y^3"), formulas("\$x^2\$ 和 \\(y^3\\)"))
    }

    @Test
    fun renderedParenthesizedMathNeverShowsRawBackslashSource() {
        val inlines = ChatRichContentParser.parseInlines("值 \\(\\frac{\\sqrt{x-1}}{x-2}\\) 有意义")
        val rendered = buildRichInlineAnnotatedString(inlines).text
        assertTrue("不能把 LaTeX 源码外露给用户: $rendered", !rendered.contains('\\'))
    }

    @Test
    fun recognizesMultiLineBlockFormula() {
        val input = """
            这是前置文字
            $$
            \frac{-b \pm \sqrt{b^2 - 4ac}}{2a}
            $$
            这是后置文字
        """.trimIndent()
        val blocks = ChatRichContentParser.parse(input)
        assertEquals(3, blocks.size)
        assertTrue(blocks[1] is ChatRichBlock.Formula)
        assertEquals("\\frac{-b \\pm \\sqrt{b^2 - 4ac}}{2a}", (blocks[1] as ChatRichBlock.Formula).source)
    }
}
