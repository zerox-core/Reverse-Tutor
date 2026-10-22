package com.reversetutor.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * LaTeX 纯文本美化兜底契约测试（纯 JVM）。
 *
 * 覆盖：嵌套 \frac、带指数 \sqrt、上下标、希腊字母与符号、定界符剥离、
 * 未知命令合理降级、残缺/半截输入（不抛异常、不残留反斜杠）与重复调用稳定性。
 */
class LatexPlainTextBeautifierTest {

    private fun assertBeautified(source: String, expected: String) {
        val actual = LatexPlainTextBeautifier.beautify(source)
        assertEquals("beautify(<$source>)", expected, actual)
        assertEquals(
            "beautify 必须稳定：同一输入重复调用结果一致",
            actual,
            LatexPlainTextBeautifier.beautify(source)
        )
    }

    // ---------- 分数 ----------

    @Test
    fun beautifiesNestedFractionsWithParentheses() {
        assertBeautified("\\frac{a}{b}", "(a)/(b)")
        assertBeautified("\\frac{1}{2}", "(1)/(2)")
        assertBeautified("\\frac{a+b}{c}", "(a+b)/(c)")
        assertBeautified("\\frac{\\frac{a}{b}}{c}", "((a)/(b))/(c)")
        assertBeautified("\\frac{\\sqrt{x-1}}{x-2}", "(√(x-1))/(x-2)")
        assertBeautified("\\dfrac{1}{2}", "(1)/(2)")
        assertBeautified("\\tfrac{a}{b}", "(a)/(b)")
    }

    // ---------- 根式 ----------

    @Test
    fun beautifiesSqrtWithAndWithoutIndex() {
        assertBeautified("\\sqrt{x}", "√(x)")
        assertBeautified("\\sqrt{x-1}", "√(x-1)")
        assertBeautified("\\sqrt[3]{x}", "³√(x)")
        assertBeautified("\\sqrt[n]{x}", "ⁿ√(x)")
        assertBeautified("\\sqrt[ab]{x}", "ᵃᵇ√(x)")
        assertBeautified("\\frac{\\sqrt[3]{y}}{2}", "(³√(y))/(2)")
    }

    // ---------- 上下标 ----------

    @Test
    fun beautifiesScriptsToUnicodeSuperAndSubscripts() {
        assertBeautified("x^{2}", "x²")
        assertBeautified("x^2", "x²")
        assertBeautified("a_{1}", "a₁")
        assertBeautified("a_1", "a₁")
        assertBeautified("E=mc^2", "E=mc²")
        assertBeautified("x^{n+1}", "xⁿ⁺¹")
        assertBeautified("a_1^2", "a₁²")
        assertBeautified("\\sum_{i=1}^{n}", "∑ᵢ₌₁ⁿ")
    }

    // ---------- 希腊字母与符号 ----------

    @Test
    fun beautifiesGreekLetters() {
        assertBeautified("\\alpha", "α")
        assertBeautified("\\beta", "β")
        assertBeautified("\\theta", "θ")
        assertBeautified("\\pi", "π")
        assertBeautified("\\alpha+\\beta=\\gamma", "α+β=γ")
        assertBeautified("\\Delta x", "Δ x")
    }

    @Test
    fun beautifiesOperatorAndRelationSymbols() {
        val cases = mapOf(
            "\\le" to "≤",
            "\\leq" to "≤",
            "\\ge" to "≥",
            "\\geq" to "≥",
            "\\ne" to "≠",
            "\\neq" to "≠",
            "\\times" to "×",
            "\\div" to "÷",
            "\\cdot" to "·",
            "\\pm" to "±",
            "\\to" to "→",
            "\\rightarrow" to "→",
            "\\infty" to "∞",
            "\\in" to "∈",
            "\\sum" to "∑",
            "\\int" to "∫",
            "\\%" to "%"
        )
        cases.forEach { (source, expected) -> assertBeautified(source, expected) }
        assertBeautified("x \\le y", "x ≤ y")
        assertBeautified("n \\to \\infty", "n → ∞")
    }

    // ---------- 定界符剥离 ----------

    @Test
    fun stripsMathDelimiters() {
        assertBeautified("$$\\frac{a}{b}$$", "(a)/(b)")
        assertBeautified("\$x^2\$", "x²")
        assertBeautified("\\(\\alpha\\)", "α")
        assertBeautified("\\[x^2\\]", "x²")
        assertBeautified("$$ x_{1} $$", " x₁ ")
    }

    @Test
    fun beautifiesChemicalEquationsWithSubscriptsAndArrows() {
        assertBeautified("\\ce{2H2 + O2 -> 2H2O}", "2H₂ + O₂ → 2H₂O")
        assertBeautified("\\ce{CaCO3 v}", "CaCO₃↓")
        assertBeautified("\\ce{2H2O -> 2H2 ^ + O2 ^}", "2H₂O → 2H₂↑ + O₂↑")
        assertBeautified("\\ce{Fe^3+ + 3OH- -> Fe(OH)3 v}", "Fe³⁺ + 3OH- → Fe(OH)₃↓")
    }

    @Test
    fun beautifiesReactionConditionArrows() {
        assertBeautified("\\ce{2H2 + O2 ->[点燃] 2H2O}", "2H₂ + O₂ —点燃→ 2H₂O")
        assertBeautified("\\ce{N2 + 3H2 <=>[高温高压] 2NH3}", "N₂ + 3H₂ —高温高压⇌ 2NH₃")
        assertBeautified("\\ce{N2 + 3H2 <=> 2NH3}", "N₂ + 3H₂ ⇌ 2NH₃")
    }

    @Test
    fun beautifiesXrightarrowConditionArrowsOutsideCe() {
        // 模型实际写法（真机截图反推）：不用 \ce，直接用 \xrightarrow 写化学方程式
        assertBeautified("2H_2 + O_2 \\xrightarrow{\\text{点燃}} 2H_2O", "2H₂ + O₂ —点燃→ 2H₂O")
        assertBeautified("A \\xrightarrow[Δ]{} B", "A —Δ→ B")
        assertBeautified("A \\xrightarrow{} B", "A → B")
        assertBeautified("A \\xleftarrow{水解} B", "A —水解← B")
        assertBeautified("A \\xrightleftharpoons{高温高压} B", "A —高温高压⇌ B")
    }

    // ---------- 未知命令降级 ----------

    @Test
    fun unknownCommandsDegradeWithoutBackslashes() {
        assertBeautified("\\unknown{x}", "x")
        assertBeautified("\\unknown{\\alpha}", "α")
        assertBeautified("\\foo", "")
        assertBeautified("\\begin{matrix}a&b\\end{matrix}", "a b")
        assertBeautified("\\displaystyle\\sum_{i=1}^{n}", "∑ᵢ₌₁ⁿ")
        assertBeautified("\\left(\\frac{1}{2}\\right)", "((1)/(2))")
        assertBeautified("\\text{当 }x>0", "当 x>0")
    }

    // ---------- 残缺/半截输入 ----------

    @Test
    fun malformedInputNeverThrowsAndNeverLeavesBackslashes() {
        val samples = listOf(
            "\\frac{1}{", "\\frac", "\\frac{\\frac{a}{b}}{", "}", "{{{", "{{{{", "}}}",
            "\\\\", "$", "$$", "\\", "\\sqrt[", "\\sqrt[abc", "\\sqrt",
            "x^", "_{", "x^{", "a^\\frac{1}", "\\text", "\\left", "\\binom{n}",
            "\\unknown{", "[[[]]]", "&", "~", "\\begin{matrix}", "\\end{", "x^{\\alpha"
        )
        samples.forEach { sample ->
            val beautified = LatexPlainTextBeautifier.beautify(sample)
            assertFalse(
                "严禁把反斜杠交给用户：beautify(<$sample>) == <$beautified>",
                beautified.contains('\\')
            )
            assertEquals(
                "beautify 必须稳定：<$sample>",
                beautified,
                LatexPlainTextBeautifier.beautify(sample)
            )
        }
    }

    @Test
    fun arbitraryPlainTextIsReturnedAsIs() {
        assertBeautified("", "")
        assertBeautified("纯中文说明", "纯中文说明")
        assertBeautified("3*4=12", "3*4=12")
        assertBeautified("速度 v = s / t", "速度 v = s / t")
    }

    @Test
    fun beautifiedMathTextIsStableUnderRepeatedBeautify() {
        val sources = listOf(
            "\\frac{\\sqrt{x-1}}{x-2}",
            "\\sqrt[n]{x}",
            "\\sum_{i=1}^{n}",
            "\\alpha\\le\\beta",
            "\$\$x^{2}+a_{1}\$\$"
        )
        sources.forEach { source ->
            val once = LatexPlainTextBeautifier.beautify(source)
            assertEquals("重复美化结果必须稳定：<$source>", once, LatexPlainTextBeautifier.beautify(once))
        }
    }
}
