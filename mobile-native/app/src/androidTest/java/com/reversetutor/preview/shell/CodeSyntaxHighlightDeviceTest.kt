package com.reversetutor.preview.shell

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
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
 * 1c-c1 模拟器实测：多语言代码块语法高亮 + 未知语言回退 + 流式半完整块不炸屏。
 * 渲染结果整屏截图写到应用外部文件目录，由 adb pull 取回人工核对配色。
 */
class CodeSyntaxHighlightDeviceTest {

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
    fun codeBlocksHighlightAcrossLanguagesAndScreenshot() {
        // ChatScreen 首帧会自动滚到底部：把要截图核对的 kotlin/python 放最后，
        // 保证高亮块在视口内；前面的消息验证存在性即可。
        val messages = listOf(
            assistantMessage("m-streaming", "```kotlin\nfun partial( = \"未闭合"),
            assistantMessage("m-formula", "\$\$E = mc^2\$\$"),
            assistantMessage("m-unknown", "```cobol\nMOVE 1 TO X.\n```"),
            assistantMessage(
                "m-bash",
                "```bash\n# 打包\nif [ -f app.apk ]; then\n  echo \$TAG 'ready'\nfi\n```"
            ),
            assistantMessage(
                "m-json",
                "```json\n{\"name\": \"小明\", \"score\": 98.5, \"passed\": true}\n```"
            ),
            assistantMessage(
                "m-python",
                "```python\ndef fib(n):\n    if n < 2:\n        return n\n" +
                    "    return fib(n - 1) + fib(n - 2)  # 递归\n```"
            ),
            assistantMessage(
                "m-kotlin",
                "```kotlin\n// 计算平方和\nfun squareSum(xs: List<Int>): Int {\n" +
                    "    var total = 0\n    for (x in xs) total += x * x\n    return total\n}\n```"
            )
        )
        compose.setContent {
            MaterialTheme {
                ChatScreen(
                    state = ChatUiState(
                        sessionTitle = "高亮实测",
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

        // 首帧自动滚到底部：kotlin（最后一条）与其复制按钮可见
        compose.onNodeWithText("代码 · kotlin").assertIsDisplayed()
        compose.onNodeWithContentDescription("复制代码 · kotlin").assertIsDisplayed()
        compose.onNodeWithText("代码 · python").assertIsDisplayed()

        // 先截图（视口内是 kotlin/python 高亮块），再逐条滚动核对其余语言
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val outFile = File(context.getExternalFilesDir(null), "c1_highlight.png")
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        FileOutputStream(outFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        assertTrue(outFile.exists() && outFile.length() > 0)

        // 逐条滚入视口确认可组合渲染（performScrollTo 失败会直接抛错），
        // 不逐条 assertIsDisplayed——贴顶边裁剪判定有抖动，显示效果以截图为准。
        compose.onNodeWithText("代码 · json").performScrollTo()
        compose.onNodeWithText("代码 · bash").performScrollTo()
        compose.onNodeWithText("代码 · cobol").performScrollTo()

        // 远端离屏项没有语义节点，performScrollTo 跨距过远会找不到；
        // 改用列表自身的滚动语义直达顶部（index 0 = 流式半完整块消息）
        val timelines = compose.onAllNodes(hasScrollToIndexAction()).fetchSemanticsNodes()
        assertTrue(timelines.isNotEmpty())
        compose.onAllNodes(hasScrollToIndexAction())[0].performScrollToIndex(0)
        compose.waitForIdle()
        // 流式半完整块：整体回退纯文本，不炸屏；公式块在顶部视口内应已组合
        compose.onNodeWithText("fun partial( = \"未闭合", substring = true).assertIsDisplayed()
        compose.onNodeWithText("公式").assertExists()

        val topFile = File(context.getExternalFilesDir(null), "c1_highlight_top.png")
        val topBitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        FileOutputStream(topFile).use { out ->
            topBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        assertTrue(topFile.exists() && topFile.length() > 0)
    }
}
