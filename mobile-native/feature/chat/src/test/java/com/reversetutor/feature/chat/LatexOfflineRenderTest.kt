package com.reversetutor.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 离线公式渲染层契约测试（纯 JVM）：
 * 渲染资源随 APK 本地加载（不联网、不上服务器），LaTeX 源码内联进 JS 时不得截断
 * `<script>` 块，且 `render.html` 与 Kotlin 侧的桥接契约必须一致。
 */
class LatexOfflineRenderTest {

    private val katexAssets: File = File("src/main/assets/katex")

    // ---------- 本地资源 ----------

    @Test
    fun katexBundleIsPackagedWithRenderPage() {
        assertTrue("缺少 KaTeX 脚本", File(katexAssets, "katex.min.js").isFile)
        assertTrue("缺少 KaTeX 样式", File(katexAssets, "katex.min.css").isFile)
        assertTrue("缺少 mhchem 化学扩展", File(katexAssets, "contrib/mhchem.min.js").isFile)
        assertTrue("缺少渲染页", File(katexAssets, LatexOfflineRender.PAGE_FILE).isFile)
        // 字体必须随包，否则公式退化成系统字体
        val fonts = File(katexAssets, "fonts").listFiles { f -> f.extension == "woff2" }.orEmpty()
        assertTrue("woff2 字体未打包（实际 ${fonts.size} 个）", fonts.size >= 20)
    }

    @Test
    fun renderPageLoadsOnlyLocalAssets() {
        val html = File(katexAssets, LatexOfflineRender.PAGE_FILE).readText()

        assertTrue(html.contains("katex.min.js"))
        assertTrue(html.contains("katex.min.css"))
        assertTrue(html.contains("contrib/mhchem.min.js"))
        assertFalse("渲染页不得引用远程资源", html.contains("http://") || html.contains("https://"))
        assertFalse("渲染页不得外连", html.contains("fetch(") || html.contains("XMLHttpRequest"))
    }

    @Test
    fun renderPageExposesBridgeContractUsedByKotlin() {
        val html = File(katexAssets, LatexOfflineRender.PAGE_FILE).readText()

        // 桥接对象名两边必须一致，否则高度回传静默失效
        assertTrue(
            "render.html 未使用桥接名 ${LatexOfflineRender.HEIGHT_BRIDGE_NAME}",
            html.contains(LatexOfflineRender.HEIGHT_BRIDGE_NAME)
        )
        // Kotlin 注入的入口函数必须存在
        assertTrue("render.html 缺少 renderFormula 入口", html.contains("function renderFormula"))
    }

    // ---------- JS 字符串转义 ----------

    @Test
    fun jsStringLiteralCannotBreakOutOfScriptBlock() {
        val literal = LatexOfflineRender.jsStringLiteral("a</script><script>alert(1)</script>")

        assertFalse("必须转义 < 以阻断 </script> 截断", literal.contains("</script>"))
        assertTrue(literal.contains("\\u003c"))
        assertTrue(literal.startsWith("\""))
        assertTrue(literal.endsWith("\""))
    }

    @Test
    fun jsStringLiteralEscapesQuotesAndBackslashes() {
        val literal = LatexOfflineRender.jsStringLiteral("\\frac{\"a\"}{b}")

        assertTrue(literal.contains("\\\\frac"))
        assertTrue(literal.contains("\\\""))
        assertFalse("裸反斜杠不得出现", literal.contains("\"\\frac"))
    }

    @Test
    fun renderCallCarriesSourceAndReadableFallback() {
        val call = LatexOfflineRender.renderCallJs("\\ce{2H2 + O2 -> 2H2O}", "2H₂ + O₂ ⟶ 2H₂O")

        assertTrue(call.startsWith("renderFormula("))
        // 反斜杠转义成 \\、箭头里的 > 转义成 \u003e，JS 解析后还原为原 LaTeX
        assertTrue(call.contains("\\\\ce{2H2 + O2 -\\u003e 2H2O}"))
        assertTrue(call.contains("2H₂ + O₂ ⟶ 2H₂O"))
    }

    @Test
    fun renderCallKeepsOrdinaryLatexIntact() {
        val call = LatexOfflineRender.renderCallJs("\\ce{Fe^3+ + 3OH- -> Fe(OH)3 v}", "Fe³⁺")

        assertTrue(call.contains("Fe^3+ + 3OH-"))
        assertTrue(call.contains("\\\\ce{"))
    }

    // ---------- 渲染路径选择 ----------

    @Test
    fun chemistryAndComplexLayoutsGoToOfflineRenderer() {
        // 化学式：原生解析器会把 \ce 当字母 ce，必须走离线 KaTeX + mhchem
        assertTrue(shouldUseOfflineRenderer("\\ce{2H2 + O2 -> 2H2O}"))
        assertTrue(shouldUseOfflineRenderer("\\pu{1.5e-3 mol}"))
        // 矩阵这类原生自绘表达不了的排版
        assertTrue(shouldUseOfflineRenderer("\\begin{matrix}a&b\\\\c&d\\end{matrix}"))
    }

    @Test
    fun simpleFormulasKeepNativeFastPath() {
        // 简单公式仍走 Compose 原生绘制，不起 WebView（列表滚动性能优先）
        assertFalse(shouldUseOfflineRenderer("\\frac{a}{b}"))
        assertFalse(shouldUseOfflineRenderer("E=mc^2"))
        assertFalse(shouldUseOfflineRenderer("x^2+y_1=\\sqrt{z}"))
    }

    @Test
    fun pageUrlPointsAtPackagedAssetDirectory() {
        assertEquals("file:///android_asset/katex/render.html", LatexOfflineRender.PAGE_URL)
    }
}
