package com.reversetutor.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionSettingsWidgetsTest {

    @Test
    fun `parses tokens separated by Chinese and English delimiters`() {
        val tokens = parseScopeTokens("立体几何、导数，概率统计,数列;圆锥曲线；函数")
        assertEquals(listOf("立体几何", "导数", "概率统计", "数列", "圆锥曲线", "函数"), tokens)
    }

    @Test
    fun `trims surrounding whitespace of each token`() {
        val tokens = parseScopeTokens("  立体几何 、 导数  ")
        assertEquals(listOf("立体几何", "导数"), tokens)
    }

    @Test
    fun `empty and blank-only input yields empty list`() {
        assertEquals(emptyList<String>(), parseScopeTokens(""))
        assertEquals(emptyList<String>(), parseScopeTokens("  、，， ;;  "))
    }

    @Test
    fun `keeps duplicate tokens as-is`() {
        // 去重由输入侧负责（重复输入会触发 duplicate 提示），解析层不做静默合并
        val tokens = parseScopeTokens("导数、导数")
        assertEquals(listOf("导数", "导数"), tokens)
    }

    @Test
    fun `single token without delimiter`() {
        assertEquals(listOf("导数"), parseScopeTokens("导数"))
    }
}
