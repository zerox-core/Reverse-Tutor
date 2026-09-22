package com.reversetutor.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1c-c2 LaTeX 公式渲染解析层契约测试（纯 JVM）。
 *
 * 覆盖：普通字符、希腊字母与符号映射、\frac、\sqrt、上下标（含组合）、分组、
 * 函数名直立、未知命令降级、流式半完整块与畸形输入的 Failure 契约（不抛异常）。
 */
class LatexMiniParserTest {

    private fun parseSuccess(source: String): List<LatexNode> {
        val result = LatexMiniParser.parse(source)
        assertTrue("expected Success for <$source>, got $result", result is LatexParseResult.Success)
        return (result as LatexParseResult.Success).nodes
    }

    private fun parseFailure(source: String) {
        val result = LatexMiniParser.parse(source)
        assertTrue("expected Failure for <$source>, got $result", result is LatexParseResult.Failure)
    }

    // ---------- 基础原子 ----------

    @Test
    fun plainAtomsParseAsRuns() {
        val nodes = parseSuccess("E = mc")
        assertEquals(listOf(LatexNode.Atom("E = mc")), nodes)
    }

    @Test
    fun scriptSplitsLastCharAsBase() {
        // mc^2：基元只是最后一个字符 c，前缀 m 保留为普通 Atom
        val nodes = parseSuccess("mc^2")
        assertEquals(
            listOf(
                LatexNode.Atom("m"),
                LatexNode.Script(LatexNode.Atom("c"), listOf(LatexNode.Atom("2")), null)
            ),
            nodes
        )
    }

    @Test
    fun scriptBaseIgnoresTrailingSpace() {
        val nodes = parseSuccess("x ^2")
        assertEquals(
            listOf(LatexNode.Script(LatexNode.Atom("x"), listOf(LatexNode.Atom("2")), null)),
            nodes
        )
    }

    @Test
    fun digitsAndOperatorsStayUprightAtoms() {
        val nodes = parseSuccess("1+2=3")
        assertEquals(listOf(LatexNode.Atom("1+2=3")), nodes)
    }

    // ---------- 希腊字母与符号 ----------

    @Test
    fun greekLettersMapToUnicode() {
        val nodes = parseSuccess("\\alpha+\\beta")
        assertEquals(
            listOf(LatexNode.MathSymbol("α"), LatexNode.Atom("+"), LatexNode.MathSymbol("β")),
            nodes
        )
    }

    @Test
    fun uppercaseGreekMaps() {
        val nodes = parseSuccess("\\Delta \\Omega")
        assertEquals(
            listOf(LatexNode.MathSymbol("Δ"), LatexNode.Atom(" "), LatexNode.MathSymbol("Ω")),
            nodes
        )
    }

    @Test
    fun relationSymbolsMap() {
        val nodes = parseSuccess("a\\leq b\\neq c\\approx d")
        val symbols = nodes.filterIsInstance<LatexNode.MathSymbol>().map { it.text }
        assertEquals(listOf("≤", "≠", "≈"), symbols)
    }

    @Test
    fun arrowsAndSetsMap() {
        val nodes = parseSuccess("x\\in A\\rightarrow B")
        val symbols = nodes.filterIsInstance<LatexNode.MathSymbol>().map { it.text }
        assertEquals(listOf("∈", "→"), symbols)
    }

    @Test
    fun bigOperatorsMap() {
        val nodes = parseSuccess("\\sum\\prod\\int")
        assertEquals(
            listOf(LatexNode.MathSymbol("∑"), LatexNode.MathSymbol("∏"), LatexNode.MathSymbol("∫")),
            nodes
        )
    }

    // ---------- 函数名与直立文本 ----------

    @Test
    fun functionNamesRenderUpright() {
        val nodes = parseSuccess("\\sin x+\\log y")
        val uprights = nodes.filterIsInstance<LatexNode.Upright>().map { it.text }
        assertEquals(listOf("sin", "log"), uprights)
    }

    @Test
    fun limIsUprightFunction() {
        val nodes = parseSuccess("\\lim_{n\\to\\infty}")
        val script = nodes.single() as LatexNode.Script
        assertEquals(LatexNode.Upright("lim"), script.base)
        assertNotNull(script.sub)
        // 下限含 \to 与 \infty 符号映射
        val subText = LatexMiniParser.plainText(script.sub!!)
        assertEquals("n→∞", subText)
    }

    @Test
    fun textCommandProducesUpright() {
        val nodes = parseSuccess("\\text{速度}")
        assertEquals(listOf(LatexNode.Upright("速度")), nodes)
    }

    // ---------- \frac ----------

    @Test
    fun fracParsesTwoArgs() {
        val nodes = parseSuccess("\\frac{1}{2}")
        assertEquals(
            listOf(
                LatexNode.Frac(listOf(LatexNode.Atom("1")), listOf(LatexNode.Atom("2")))
            ),
            nodes
        )
    }

    @Test
    fun fracSupportsSingleTokenArgs() {
        val nodes = parseSuccess("\\frac ab")
        assertEquals(
            listOf(
                LatexNode.Frac(listOf(LatexNode.Atom("a")), listOf(LatexNode.Atom("b")))
            ),
            nodes
        )
    }

    @Test
    fun fracNests() {
        val nodes = parseSuccess("\\frac{\\frac{1}{2}}{3}")
        val outer = nodes.single() as LatexNode.Frac
        assertTrue(outer.numerator.single() is LatexNode.Frac)
        assertEquals(listOf(LatexNode.Atom("3")), outer.denominator)
    }

    // ---------- \sqrt ----------

    @Test
    fun sqrtParsesRadicand() {
        val nodes = parseSuccess("\\sqrt{x}")
        assertEquals(listOf(LatexNode.Sqrt(null, listOf(LatexNode.Atom("x")))), nodes)
    }

    @Test
    fun sqrtParsesOptionalIndex() {
        val nodes = parseSuccess("\\sqrt[3]{x}")
        assertEquals(
            listOf(LatexNode.Sqrt(listOf(LatexNode.Atom("3")), listOf(LatexNode.Atom("x")))),
            nodes
        )
    }

    // ---------- 上下标 ----------

    @Test
    fun subscriptParses() {
        val nodes = parseSuccess("a_i")
        assertEquals(
            listOf(LatexNode.Script(LatexNode.Atom("a"), null, listOf(LatexNode.Atom("i")))),
            nodes
        )
    }

    @Test
    fun combinedScriptsBothOrders() {
        val first = parseSuccess("x^2_i").single() as LatexNode.Script
        val second = parseSuccess("x_i^2").single() as LatexNode.Script
        assertEquals(LatexNode.Atom("x"), first.base)
        assertEquals(listOf(LatexNode.Atom("2")), first.sup)
        assertEquals(listOf(LatexNode.Atom("i")), first.sub)
        assertEquals(first, second)
    }

    @Test
    fun scriptOnGroupUsesWholeGroupAsBase() {
        val nodes = parseSuccess("{ab}^2")
        val script = nodes.single() as LatexNode.Script
        assertEquals(
            LatexNode.Group(listOf(LatexNode.Atom("ab"))),
            script.base
        )
    }

    @Test
    fun scriptOnCommandUsesCommandAsBase() {
        val nodes = parseSuccess("\\sum_{i=1}^{n}")
        val script = nodes.single() as LatexNode.Script
        assertEquals(LatexNode.MathSymbol("∑"), script.base)
        assertEquals(listOf(LatexNode.Atom("n")), script.sup)
        assertEquals(listOf(LatexNode.Atom("i=1")), script.sub)
    }

    @Test
    fun scriptArgAcceptsCommand() {
        val nodes = parseSuccess("x^\\alpha")
        val script = nodes.single() as LatexNode.Script
        assertEquals(listOf(LatexNode.MathSymbol("α")), script.sup)
    }

    // ---------- 分组、括号与杂项 ----------

    @Test
    fun bracesGroupChildren() {
        val nodes = parseSuccess("{a+b}")
        assertEquals(listOf(LatexNode.Group(listOf(LatexNode.Atom("a+b")))), nodes)
    }

    @Test
    fun leftRightDelimitersRenderAsPlain() {
        val nodes = parseSuccess("\\left(\\frac{1}{2}\\right)")
        assertEquals(LatexNode.Atom("("), nodes.first())
        assertEquals(LatexNode.Atom(")"), nodes.last())
        assertTrue(nodes[1] is LatexNode.Frac)
    }

    @Test
    fun binomExpandsToParenFrac() {
        val nodes = parseSuccess("\\binom{n}{k}")
        val group = nodes.single() as LatexNode.Group
        assertEquals(LatexNode.Atom("("), group.children.first())
        assertTrue(group.children[1] is LatexNode.Frac)
        assertEquals(LatexNode.Atom(")"), group.children.last())
    }

    @Test
    fun escapesRenderLiteral() {
        val nodes = parseSuccess("50\\% \\{a\\}")
        val text = LatexMiniParser.plainText(nodes)
        assertEquals("50% {a}", text)
    }

    @Test
    fun overlineAppendsCombiningMark() {
        val nodes = parseSuccess("\\overline{AB}")
        val upright = nodes.single() as LatexNode.Upright
        // 组合字符码点与实现保持一致即可：断言前缀与「原字符数+1 组合符」结构
        assertTrue(upright.text.startsWith("AB"))
        assertEquals(3, upright.text.length)
    }

    // ---------- 降级与失败契约 ----------

    @Test
    fun unknownCommandDegradesToLiteralText() {
        val nodes = parseSuccess("\\foobar x")
        assertEquals(
            listOf(LatexNode.Atom("\\foobar"), LatexNode.Atom(" x")),
            nodes
        )
    }

    @Test
    fun emptySourceFails() {
        parseFailure("")
        parseFailure("   ")
    }

    @Test
    fun streamingPartialFracFails() {
        parseFailure("\\frac{1}{")
        parseFailure("\\frac{1}")
        parseFailure("\\frac")
    }

    @Test
    fun streamingPartialSqrtFails() {
        parseFailure("\\sqrt{")
        parseFailure("\\sqrt[3]{")
        parseFailure("\\sqrt[3")
    }

    @Test
    fun streamingPartialScriptFails() {
        parseFailure("x^")
        parseFailure("a_")
        parseFailure("x^{2")
    }

    @Test
    fun unbalancedBracesFail() {
        parseFailure("{a")
        parseFailure("a}")
        parseFailure("\\text{abc")
    }

    @Test
    fun danglingBackslashFails() {
        parseFailure("a\\")
    }

    @Test
    fun danglingScriptWithoutBaseFails() {
        parseFailure("^2")
    }

    @Test
    fun duplicateSuperscriptFails() {
        parseFailure("x^1^2")
    }

    @Test
    fun parseNeverThrowsOnArbitraryInput() {
        val samples = listOf(
            "}}}{{{", "\\frac}{}{", "^^^__", "\\sqrt[", "\\left",
            "x^\\frac{1}", "\\unknown{", "a&b", "}}}}", "\\text", "_{",
            "$$", "[[[]]]", "\\binom{n}"
        )
        samples.forEach { sample ->
            val result = LatexMiniParser.parse(sample)
            assertTrue(
                "result must be Success or Failure for <$sample>",
                result is LatexParseResult.Success || result is LatexParseResult.Failure
            )
        }
    }

    // ---------- 行内拍平 ----------

    @Test
    fun flattenMarksSuperscript() {
        val nodes = parseSuccess("E=mc^2")
        val segments = LatexInlineFlattener.flatten(nodes)
        val sup = segments.filter { it.style == LatexSegmentStyle.Superscript }
        assertEquals(listOf("2"), sup.map { it.text })
        assertEquals("E=mc", segments.first().text)
    }

    @Test
    fun flattenMarksSubscript() {
        val nodes = parseSuccess("a_i")
        val segments = LatexInlineFlattener.flatten(nodes)
        val sub = segments.filter { it.style == LatexSegmentStyle.Subscript }
        assertEquals(listOf("i"), sub.map { it.text })
    }

    @Test
    fun flattenFracLinearizesWithParensRule() {
        val simple = LatexInlineFlattener.flatten(parseSuccess("\\frac{1}{2}"))
        assertEquals("1/2", simple.joinToString("") { it.text })
        val complex = LatexInlineFlattener.flatten(parseSuccess("\\frac{a+b}{c}"))
        assertEquals("(a+b)/c", complex.joinToString("") { it.text })
        val bothComplex = LatexInlineFlattener.flatten(parseSuccess("\\frac{a+b}{c+d}"))
        assertEquals("(a+b)/(c+d)", bothComplex.joinToString("") { it.text })
    }

    @Test
    fun flattenSqrtLinearizes() {
        val plain = LatexInlineFlattener.flatten(parseSuccess("\\sqrt{x}"))
        assertEquals("√(x)", plain.joinToString("") { it.text })
        val indexed = LatexInlineFlattener.flatten(parseSuccess("\\sqrt[3]{x}"))
        assertEquals("3√(x)", indexed.joinToString("") { it.text })
    }

    @Test
    fun flattenSumWithLimitsKeepsScripts() {
        val segments = LatexInlineFlattener.flatten(parseSuccess("\\sum_{i=1}^{n}"))
        assertEquals("∑", segments.first().text)
        assertEquals(
            listOf("i=1"),
            segments.filter { it.style == LatexSegmentStyle.Subscript }.map { it.text }
        )
        assertEquals(
            listOf("n"),
            segments.filter { it.style == LatexSegmentStyle.Superscript }.map { it.text }
        )
    }

    @Test
    fun flattenMergesAdjacentSameStyle() {
        val segments = LatexInlineFlattener.flatten(parseSuccess("\\alpha\\beta"))
        assertEquals(listOf(LatexSegment("αβ")), segments)
    }

    @Test
    fun inlineBuilderReturnsNullOnFailure() {
        assertNull(latexInlineAnnotatedString("\\frac{1}{"))
        assertNull(latexInlineAnnotatedString(""))
        assertNotNull(latexInlineAnnotatedString("E=mc^2"))
    }

    @Test
    fun inlineBuilderRendersUnicodeText() {
        val rendered = latexInlineAnnotatedString("\\alpha+\\beta=\\gamma")
        assertNotNull(rendered)
        assertEquals("α+β=γ", rendered!!.text)
    }

    @Test
    fun inlineBuilderKeepsPlainTextContract() {
        // 拍平后的可见文本必须与节点树 plainText 一致（除分数/根式线性化规则）
        val rendered = latexInlineAnnotatedString("x^2+y_1=\\sqrt{z}")
        assertNotNull(rendered)
        assertEquals("x2+y1=√(z)", rendered!!.text)
    }
}
