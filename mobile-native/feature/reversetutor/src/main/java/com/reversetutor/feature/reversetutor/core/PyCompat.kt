package com.reversetutor.feature.reversetutor.core

import java.math.BigDecimal
import java.math.RoundingMode

/** Python 数值语义兼容（对拍必需的公共工具）。 */
object PyCompat {

    /**
     * Python round(x, digits)：银行家舍入（HALF_EVEN）。
     * 对拍关键：round(0.845, 2) = 0.84（HALF_UP 会得 0.85）。
     * BigDecimal.valueOf 走 Double.toString 规范表示，与 Python repr 对齐。
     */
    fun round(x: Double, digits: Int = 0): Double {
        val bd = BigDecimal.valueOf(x).setScale(digits, RoundingMode.HALF_EVEN)
        return bd.toDouble()
    }

    /** Python clamp(v, lo, hi)。 */
    fun clamp(x: Double, lo: Double, hi: Double): Double = x.coerceIn(lo, hi)
}
