package com.reversetutor.feature.reversetutor.guard

/**
 * 数学正确性守卫（规格源：spike app/tools/mathcheck.py，design v1.1 §六）。
 *
 * 扫描生成文本中的已知错误数学模式。纯正则实现，无符号计算依赖。
 *
 * WRONG:  x² + bx = (x + b/2)²
 * RIGHT:  x² + bx = (x + b/2)² - (b/2)²
 * RIGHT:  x² + 6x = (x + 3)² - 9
 *
 * make_deliberate_mistake 产生的故意错误允许出现在学生发言里——守卫只应用于
 * 面向学生的最终话语与生成类工具输出；豁免是调用方的编排责任（spike 同款）。
 */
object MathGuard {

    data class Violation(
        val pattern: String,   // 描述
        val matched: String,   // 命中片段
        val correction: String // 修正说法
    )

    private data class WrongPattern(
        val regex: Regex,
        val description: String,
        val correction: String
    )

    private val wrongPatterns: List<WrongPattern> = listOf(
        WrongPattern(
            Regex("""x\s*[²2]\s*\+\s*bx\s*=\s*\(x\s*\+\s*b\s*/\s*2\s*\)\s*[²2]\s*(?!\s*[-−]\s*\(b\s*/\s*2\s*\)\s*[²2])""", RegexOption.IGNORE_CASE),
            "x² + bx ≠ (x + b/2)²（缺少常数修正项）",
            "x² + bx = (x + b/2)² - (b/2)²"
        ),
        WrongPattern(
            Regex("""x\s*[²2]\s*\+\s*(\d+)\s*x\s*=\s*\(x\s*\+\s*(\d+)\s*\)\s*[²2]\s*(?!\s*[-−])""", RegexOption.IGNORE_CASE),
            "x² + kx ≠ (x + k/2)²（缺少常数修正项）",
            "x² + 6x = (x + 3)² - 9"
        ),
        WrongPattern(
            // 逐字可选「横/坐/标」：修复此前 [横坐]?标? 吃不满「横坐标」三字的漏报
            //（spike s1 实录句式「顶点横坐标是 (b/2a, …)」此前不命中）
            Regex("""顶点(?:横)?(?:坐)?标?\s*(?:是|为)\s*\(\s*b\s*/\s*2a\s*,"""),
            "顶点横坐标是 -b/2a（不是 +b/2a）",
            "顶点横坐标 x = -b/(2a)"
        ),
        WrongPattern(
            // 否定前瞻排除二项式平方尾缀「)²」：正确推导 (x + b/2a)² 不是违规
            // （2026-10-14 s1 spike 误报回归；真违规如「顶点横坐标是 +b/2a」仍命中）
            Regex("""\+\s*b\s*/\s*\(?\s*2\s*a(?!\s*[)）]\s*[²2^])"""),
            "顶点横坐标是 -b/(2a)（带负号，不是 +b/2a）",
            "顶点横坐标 x = -b/(2a)"
        )
    )

    /** 返回文本中发现的全部违规。 */
    fun scanMath(text: String): List<Violation> {
        if (text.isEmpty()) return emptyList()
        val violations = mutableListOf<Violation>()
        for (p in wrongPatterns) {
            for (m in p.regex.findAll(text)) {
                violations.add(Violation(p.description, m.value, p.correction))
            }
        }
        return violations
    }

    data class CompletingSquareCheck(
        val leftExpression: String,
        val rightExpression: String,
        val constantTerm: Double,
        val vertexX: Double
    )

    /** 工具使用的规范数值校验：x² + bx = (x + b/2)² - (b/2)²。 */
    fun verifyCompletingSquare(b: Double): CompletingSquareCheck {
        val half = b / 2
        val sign = if (half >= 0) "+" else "-"
        return CompletingSquareCheck(
            leftExpression = "x² + ${g(b)}x",
            rightExpression = "(x $sign ${g(kotlin.math.abs(half))})² - ${g(half * half)}",
            constantTerm = -(half * half),
            vertexX = -half
        )
    }

    /** Python %g 等价格式化：整数去小数点，其余原样。 */
    private fun g(v: Double): String {
        return if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
    }
}
