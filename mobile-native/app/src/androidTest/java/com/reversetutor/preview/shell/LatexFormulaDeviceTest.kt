package com.reversetutor.preview.shell

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.platform.app.InstrumentationRegistry
import com.reversetutor.core.model.MessageRole
import com.reversetutor.feature.chat.ChatComposerState
import com.reversetutor.feature.chat.ChatScreen
import com.reversetutor.feature.chat.ChatTimelineItem
import com.reversetutor.feature.chat.ChatUiState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.FileOutputStream

/**
 * 1c-c2 模拟器实测：LaTeX 块级公式自绘 + 行内公式上下标 + 解析失败回退源码 + 流式半完整块不炸屏。
 * 渲染结果整屏截图写到应用外部文件目录，由 adb pull 取回人工核对排版。
 */
class LatexFormulaDeviceTest {

    @get:Rule
    val compose = createComposeRule()

    private fun assistantMessage(id: String, text: String) = ChatTimelineItem(
        id = id,
        spaceId = "space-1",
        role = MessageRole.Assistant,
        roleLabel = "林澈",
        text = text,
        createdAtEpochMillis = 1_000L,
        attachmentLabels = emptyList(),
        attachments = emptyList(),
        quoteLabel = null
    )

    @Test
    fun latexFormulasRenderAndScreenshot() {
        // ChatScreen 首帧自动滚到底部：把要截图核对的二次求根公式放最后；
        // 前面的消息验证存在性即可。
        val messages = listOf(
            // 流式半完整块：$$ 未闭合，整条消息回退纯文本，不炸屏
            assistantMessage("m-streaming", "\$\$\\frac{1}{"),
            // 已闭合但内部结构不完整：解析失败回退源码块
            assistantMessage("m-malformed", "\$\$\\frac{1}\$\$"),
            // 行内公式：上下标 + 分数线性化 + 希腊字母
            assistantMessage(
                "m-inline",
                "由 \$E=mc^2\$ 与 \$\\alpha+\\beta\\leq\\gamma\$，得 \$x=\\frac{a+b}{c}\$ 成立。"
            ),
            // 块级：求和上下限 + 嵌套分数
            assistantMessage("m-sum", "\$\$\\sum_{i=1}^{n} i = \\frac{n(n+1)}{2}\$\$"),
            // 块级：根式（含根指数）+ 上下标组合
            assistantMessage("m-sqrt", "\$\$\\sqrt[3]{x^3+y_1} = x\\sqrt{1+\\frac{y_1}{x^3}}\$\$"),
            // 块级：二次求根公式（首屏视口核对项，放最后）
            assistantMessage("m-quadratic", "\$\$x = \\frac{-b \\pm \\sqrt{b^2-4ac}}{2a}\$\$")
        )
        compose.setContent {
            MaterialTheme {
                ChatScreen(
                    state = ChatUiState(
                        sessionTitle = "公式实测",
                        learnerName = "林澈",
                        learnerStatus = "学习者",
                        contextPath = "",
                        messages = messages,
                        composer = ChatComposerState(text = "")
                    ),
                    onComposerTextChange = {},
                    onSendMessage = {},
                    onCancelQuote = {},
                    onCreateImageDraft = {},
                    onCancelImageDraft = {},
                    onMessageAction = { _, _ -> }
                )
            }
        }
        compose.waitForIdle()

        // 首帧滚到底：求根公式块在视口内，公式卡片头部与复制按钮可见
        assertTrue(compose.onAllNodesWithText("公式").fetchSemanticsNodes().isNotEmpty())
        assertTrue(
            compose.onAllNodesWithContentDescription("复制公式").fetchSemanticsNodes().isNotEmpty()
        )

        // 底部截图（视口内是 quadratic / sqrt 块级公式）
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val outFile = File(context.getExternalFilesDir(null), "c2_formula.png")
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        FileOutputStream(outFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        assertTrue(outFile.exists() && outFile.length() > 0)

        // 列表直达顶部（index 0 = 流式半完整块消息）
        val timelines = compose.onAllNodes(hasScrollToIndexAction()).fetchSemanticsNodes()
        assertTrue(timelines.isNotEmpty())
        compose.onAllNodes(hasScrollToIndexAction())[0].performScrollToIndex(0)
        compose.waitForIdle()

        // 流式半完整块：整体回退纯文本展示原始字符，不炸屏
        compose.onNodeWithText("\\frac{1}{", substring = true).assertIsDisplayed()

        val topFile = File(context.getExternalFilesDir(null), "c2_formula_top.png")
        val topBitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        FileOutputStream(topFile).use { out ->
            topBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        assertTrue(topFile.exists() && topFile.length() > 0)

        // 行内公式拍平文本在段落语义里（E=mc2 为上下标拍平后的连续文本）
        compose.onNodeWithText("E=mc", substring = true).assertExists()
    }
}
