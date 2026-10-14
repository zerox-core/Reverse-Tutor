package com.reversetutor.feature.reversetutor

import com.reversetutor.feature.reversetutor.guard.MathGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MathGuard 对拍测试：期望值由 spike app/tools/mathcheck.py 实际运行输出固定。
 */
class MathGuardTest {

    @Test
    fun scanCatchesWrongIdentity() {
        // Python: scan_math('x² + bx = (x + b/2)²') → 1 violation
        val v = MathGuard.scanMath("x² + bx = (x + b/2)²")
        assertEquals(1, v.size)
        assertEquals("x² + bx ≠ (x + b/2)²（缺少常数修正项）", v[0].pattern)
        assertEquals("x² + bx = (x + b/2)²", v[0].matched)
    }

    @Test
    fun scanCatchesWrongNumericIdentity() {
        // Python: scan_math('x² + 6x = (x + 3)²') → 1 violation
        val v = MathGuard.scanMath("x² + 6x = (x + 3)²")
        assertEquals(1, v.size)
        assertEquals("x² + kx ≠ (x + k/2)²（缺少常数修正项）", v[0].pattern)
        assertEquals("x² + 6x = (x + 3)²", v[0].matched)
    }

    @Test
    fun correctFormsPass() {
        // 正确写法、正确顶点（带负号）、修正后写法都不违规
        assertTrue(MathGuard.scanMath("x² + 6x = (x + 3)² - 9").isEmpty())
        assertTrue(MathGuard.scanMath("顶点横坐标 x = -b/(2a)").isEmpty())
        assertTrue(MathGuard.scanMath("顶点横坐标是 (-b/2a, -8)").isEmpty())
    }

    @Test
    fun scanCatchesVertexBCoordinateClaim() {
        // 回归：第三条正则此前写漏「横」字（[横坐]?标? 吃不满「横坐标」），
        // 「顶点横坐标是 (b/2a, …)」这一 spike s1 同款句式漏报；修复后应命中。
        val v = MathGuard.scanMath("顶点横坐标是 (b/2a, 0)")
        assertEquals(1, v.size)
        assertEquals("顶点横坐标是 -b/2a（不是 +b/2a）", v[0].pattern)
        assertEquals("顶点横坐标是 (b/2a,", v[0].matched)
    }

    @Test
    fun emptyTextPasses() {
        assertTrue(MathGuard.scanMath("").isEmpty())
    }

    @Test
    fun correctBinomialSquaredNotFlagged() {
        // 回归（2026-10-14 s1 spike 实录）：正确的二项式平方 (x + b/2a)² 曾被
        // 第四条正则（无上下文的 \+b/2a）误报为「顶点横坐标是 +b/2a」。
        assertTrue(
            MathGuard.scanMath("那顶点横坐标就是 (x + b/2a)²，负号是括号里那个加号带出来的").isEmpty()
        )
        assertTrue(MathGuard.scanMath("写成 (x+b/2a)² 再减 (b/2a)² 才对").isEmpty())
    }

    @Test
    fun stillCatchesBarePlusB2aClaim() {
        // 收窄后不得漏掉真违规（与 ToolLoopTest.runTurnMathGuardViolationCaptured 同源）
        assertTrue(MathGuard.scanMath("我知道了，顶点横坐标是 +b/2a！").isNotEmpty())
        assertTrue(MathGuard.scanMath("顶点是 +b/(2a) 就对了").isNotEmpty())
    }

    @Test
    fun verifyCompletingSquareMatchesPython() {
        // Python: verify_completing_square(6)
        val a = MathGuard.verifyCompletingSquare(6.0)
        assertEquals("x² + 6x", a.leftExpression)
        assertEquals("(x + 3)² - 9", a.rightExpression)
        assertEquals(-9.0, a.constantTerm, 1e-9)
        assertEquals(-3.0, a.vertexX, 1e-9)
        // Python: verify_completing_square(-4)（注意 left 的符号拼接与 Python 一致）
        val b = MathGuard.verifyCompletingSquare(-4.0)
        assertEquals("x² + -4x", b.leftExpression)
        assertEquals("(x - 2)² - 4", b.rightExpression)
        assertEquals(-4.0, b.constantTerm, 1e-9)
        assertEquals(2.0, b.vertexX, 1e-9)
    }
}
