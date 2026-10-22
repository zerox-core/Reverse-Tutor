package com.reversetutor.feature.chat

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * 离线公式渲染层（2026-10-17 用户拍板「把渲染放在用户本地运行，不把压力给到服务器」）。
 *
 * 全部资源随 APK 打包在 `assets/katex/`（KaTeX + mhchem 化学扩展 + woff2 字体，约 0.57MB）：
 * 渲染 100% 在设备本地完成，不联网、不上服务器——服务器只负责产出 LaTeX 文本。
 *
 * 只在原生自绘表达不了的场景启用（化学式、矩阵/cases 等）；普通 `\frac`/`\sqrt`/上下标
 * 仍走 [RichFormulaBlock] 的 Compose 原生绘制，避免每个公式都起一个 WebView 拖累列表滚动。
 */
internal object LatexOfflineRender {

    /** 资源基准 URL（`file://`，相对路径由此解析到 assets/katex/）。 */
    const val ASSET_BASE_URL = "file:///android_asset/katex/"

    /** 渲染页文件名（真实 file:// 文档，同目录子资源与字体加载支持最好）。 */
    const val PAGE_FILE = "render.html"

    /** JS 侧高度回传对象名；`render.html` 内按此名调用，两边必须一致。 */
    const val HEIGHT_BRIDGE_NAME = "AndroidRender"

    /** 渲染页完整 URL。 */
    val PAGE_URL: String get() = ASSET_BASE_URL + PAGE_FILE

    /** 注入渲染调用的 JS：`renderFormula("<源码>", "<兜底文本>")`。 */
    fun renderCallJs(source: String, fallbackText: String): String =
        "renderFormula(${jsStringLiteral(source)}, ${jsStringLiteral(fallbackText)})"

    /**
     * 转义成可安全内联进 `<script>` 的 JS 字符串字面量。
     *
     * `<` / `>` / `&` 转成 `\uXXXX`：LaTeX 里出现 `</script>` 会提前截断脚本块。
     */
    fun jsStringLiteral(text: String): String {
        val out = StringBuilder(text.length + 16)
        out.append('"')
        for (c in text) {
            when {
                c == '\\' -> out.append("\\\\")
                c == '"' -> out.append("\\\"")
                c == '\n' -> out.append("\\n")
                c == '\r' -> out.append("\\r")
                c == '\t' -> out.append("\\t")
                c == '<' -> out.append("\\u003c")
                c == '>' -> out.append("\\u003e")
                c == '&' -> out.append("\\u0026")
                c == '\u2028' -> out.append("\\u2028")
                c == '\u2029' -> out.append("\\u2029")
                c.code < 0x20 -> out.append("\\u%04x".format(c.code))
                else -> out.append(c)
            }
        }
        out.append('"')
        return out.toString()
    }
}

/** JS → Compose 的高度回传桥：只暴露一个只读高度上报，不提供任何取数据能力。 */
private class RenderHeightBridge(private val onHeightPx: (Int) -> Unit) {
    @JavascriptInterface
    fun reportHeight(px: Int) {
        onHeightPx(px)
    }
}

private val MIN_BODY_HEIGHT = 44.dp
private val MAX_BODY_HEIGHT = 380.dp

/**
 * 离线公式卡片：头部（标签 + 复制源码）+ 本地 KaTeX 渲染正文。
 * 高度由渲染结果回传后确定，卡片内部不出滚动条，不影响外层会话列表滚动。
 */
@Composable
internal fun LatexOfflineRenderCard(
    source: String,
    onCopySource: (String) -> ChatClipboardResult
) {
    val fallback = remember(source) { LatexPlainTextBeautifier.beautify(source) }
    val density = LocalDensity.current
    var reportedPx by remember(source) { mutableStateOf(0) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val bodyHeight: Dp = with(density) {
        reportedPx.toDp().coerceIn(MIN_BODY_HEIGHT, MAX_BODY_HEIGHT)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = LatexFormulaPalette.card,
        shape = RoundedCornerShape(7.dp),
        border = BorderStroke(1.dp, LatexFormulaPalette.border)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (containsChemistryCommand(source)) "化学方程式" else "公式",
                    modifier = Modifier.weight(1f),
                    color = LatexFormulaPalette.label,
                    fontSize = 11.sp
                )
                IconButton(onClick = { onCopySource(source) }, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Filled.ContentCopy,
                        contentDescription = "复制公式源码",
                        modifier = Modifier.size(18.dp),
                        tint = LatexFormulaPalette.label
                    )
                }
            }
            Box {
                // 原生兜底：KaTeX 还没回传高度（首次加载中、或该机型 WebView 拒绝
                // file:// 资源）时先显示可读文本，绝不留一张空白卡片；渲染就绪后自动让位。
                if (reportedPx == 0) {
                    Text(
                        text = fallback,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        color = LatexFormulaPalette.ink,
                        fontSize = 14.sp,
                        lineHeight = 22.sp
                    )
                }
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            // 本地 assets 的 KaTeX、字体需要文件访问；网络彻底封死——
                            // 渲染不依赖任何远程资源，也不允许公式内容触发外连。
                            settings.allowFileAccess = true
                            settings.allowFileAccessFromFileURLs = true
                            settings.allowUniversalAccessFromFileURLs = true
                            settings.blockNetworkLoads = true
                            settings.allowContentAccess = false
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            isVerticalScrollBarEnabled = false
                            isHorizontalScrollBarEnabled = false
                            addJavascriptInterface(
                                RenderHeightBridge { px -> mainHandler.post { reportedPx = px } },
                                LatexOfflineRender.HEIGHT_BRIDGE_NAME
                            )
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView, url: String?) {
                                    // 页面就绪后按当前源码渲染（加载期间源码可能已更新）
                                    view.renderCurrentSource()
                                }
                            }
                            loadUrl(LatexOfflineRender.PAGE_URL)
                        }
                    },
                    update = { webView ->
                        // 只在源码变化时重新渲染：重组重复渲染会闪屏并反复触发高度回传。
                        // 用 View.tag 记录而非 remember —— 列表回收后重建 WebView 时会重新加载。
                        if (webView.tag != source) {
                            webView.tag = source
                            webView.renderCurrentSource()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(bodyHeight)
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        // 渲染就绪前保持透明，让上面的原生兜底文本可见
                        .alpha(if (reportedPx > 0) 1f else 0f)
                )
            }
        }
    }
}

/**
 * 按 WebView 上记录的源码重新渲染。
 *
 * 页面可能还没加载完（`renderFormula` 尚未定义）——此时静默失败即可：
 * [WebViewClient.onPageFinished] 会再调用一次，最终状态一定正确。
 */
private fun WebView.renderCurrentSource() {
    val current = tag as? String ?: return
    val fallback = LatexPlainTextBeautifier.beautify(current)
    evaluateJavascript(LatexOfflineRender.renderCallJs(current, fallback), null)
}

/** 公式块是否应交给离线 KaTeX 渲染：化学式，或原生自绘表达不了的复杂排版。 */
internal fun shouldUseOfflineRenderer(source: String): Boolean =
    containsChemistryCommand(source) || latexBlockNodesForDrawing(source) == null
