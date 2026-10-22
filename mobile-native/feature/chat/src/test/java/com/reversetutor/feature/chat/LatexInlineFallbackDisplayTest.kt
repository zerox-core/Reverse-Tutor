package com.reversetutor.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 公式显示兜底契约测试（纯 JVM）：排版层拿不出结果时，用户看到的是美化后的
 * 可读 Unicode 数学文本，而不是带反斜杠的 LaTeX 源码。
 */
class LatexInlineFallbackDisplayTest {

    // ---------- 行内路径 ----------

    @Test
    fun inlineFallbackShowsBeautifiedTextInsteadOfRawSource() {
        // \frac{1}{ 解析失败（流式半完整块）→ 兜底必须是美化文本
        val fallback = latexInlineDisplayAnnotatedString("\\frac{1}{")

        assertFalse("兜底不得显示反斜杠源码", fallback.text.contains('\\'))
        assertEquals("(1)/()", fallback.text)
    }

    @Test
    fun inlineFallbackBeautifiesUnknownCommandLeaks() {
        // 解析成功但树里残留未识别命令：自绘会把 "\bmod" 直接暴露给用户 → 走美化文本
        val rendered = latexInlineDisplayAnnotatedString("a \\bmod b")

        assertFalse(rendered.text.contains('\\'))
        assertEquals("a mod b", rendered.text)
    }

    @Test
    fun inlineFallbackKeepsReadableMathForHalfTypedFormula() {
        val rendered = latexInlineDisplayAnnotatedString("\\alpha + \\sqrt{")

        assertFalse(rendered.text.contains('\\'))
        assertEquals("α + √", rendered.text)
    }

    @Test
    fun successfulMiniLayoutIsUnchanged() {
        // 能排版成功时行为不变：仍走既有拍平 + 上下标样式
        assertEquals("E=mc2", latexInlineDisplayAnnotatedString("E=mc^2").text)
        assertEquals("α+β=γ", latexInlineDisplayAnnotatedString("\\alpha+\\beta=\\gamma").text)
        assertEquals("x2+y1=√(z)", latexInlineDisplayAnnotatedString("x^2+y_1=\\sqrt{z}").text)
        // 既有 API 的 null 契约不变（调用方按需回退）
        assertNull(latexInlineAnnotatedString("\\frac{1}{"))
        assertNotNull(latexInlineAnnotatedString("E=mc^2"))
    }

    @Test
    fun richInlineBuilderUsesBeautifiedFallbackForFormulaSegments() {
        val source = "答案是 " + '$' + "\\frac{1}{" + '$' + " 哦"
        val inlines = ChatRichContentParser.parseInlines(source)
        val formula = inlines.filterIsInstance<ChatRichInline.Formula>().single()
        assertEquals("\\frac{1}{", formula.source)

        val annotated = buildRichInlineAnnotatedString(inlines)

        assertEquals("答案是 (1)/() 哦", annotated.text)
        assertFalse(annotated.text.contains('\\'))
        assertTrue(annotated.spanStyles.isNotEmpty())
    }

    // ---------- 化学式（mhchem）路径 ----------

    @Test
    fun chemistryFormulaRendersAsReadableLinearFormula() {
        val inline = latexInlineDisplayAnnotatedString("\\ce{2H2 + O2 -> 2H2O}")

        assertEquals("2H₂ + O₂ → 2H₂O", inline.text)
        assertFalse("化学式不得显示反斜杠源码", inline.text.contains('\\'))
        // 块级：化学式一律不自绘（自绘会把 \ce 当字母 ce 画出来），必须回退美化文本
        assertNull(latexBlockNodesForDrawing("\\ce{2H2 + O2 -> 2H2O}"))
    }

    @Test
    fun chemistryIonAndPrecipitateMarksSurvive() {
        assertEquals(
            "Fe³⁺ + 3OH- → Fe(OH)₃↓",
            latexInlineDisplayAnnotatedString("\\ce{Fe^3+ + 3OH- -> Fe(OH)3 v}").text
        )
        assertEquals("CaCO₃↓", latexInlineDisplayAnnotatedString("\\ce{CaCO3 v}").text)
    }

    @Test
    fun chemistryReactionConditionIsKept() {
        assertEquals(
            "2H₂ + O₂ —点燃→ 2H₂O",
            latexInlineDisplayAnnotatedString("\\ce{2H2 + O2 ->[点燃] 2H2O}").text
        )
    }

    // ---------- 块级路径 ----------

    @Test
    fun blockDrawingIsSkippedWhenUnknownCommandsWouldLeakBackslashes() {
        // 解析成功且无残留命令：照旧走 Compose 2D 自绘（含分数线）
        assertNotNull(latexBlockNodesForDrawing("\\frac{a}{b}"))
        // 流式半完整块：解析失败 → 走美化文本
        assertNull(latexBlockNodesForDrawing("\\frac{1}{"))
        // 解析成功但残留未识别命令：自绘会画出反斜杠 → 走美化文本
        assertNull(latexBlockNodesForDrawing("a \\bmod b"))
    }

    @Test
    fun blockFallbackBeautifiesSourceForDisplay() {
        val displayed = LatexPlainTextBeautifier.beautify("\\begin{matrix}a&b\\end{matrix}")

        assertFalse(displayed.contains('\\'))
        assertEquals("a b", displayed)
    }
}
