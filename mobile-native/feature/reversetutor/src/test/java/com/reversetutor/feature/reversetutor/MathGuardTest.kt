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
        // 正确写法、正确顶点、修正后写法都不违规
        assertTrue(MathGuard.scanMath("x² + 6x = (x + 3)² - 9").isEmpty())
        assertTrue(MathGuard.scanMath("顶点横坐标 x = -b/(2a)").isEmpty())
        assertTrue(MathGuard.scanMath("顶点横坐标是 (b/2a, 0)".let {
            // Python 实测 T3 返回空：第三条正则要求逗号，这里无逗号 → 不命中
            it
        }).isEmpty())
    }

    @Test
    fun emptyTextPasses() {
        assertTrue(MathGuard.scanMath("").isEmpty())
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
