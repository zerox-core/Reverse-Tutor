package com.reversetutor.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2026-10-15 真机反馈回归：新建会话的开场白必须是「学生第一人称说话」。
 *
 * 此前 `openingMessage = "我是${learnerName}。${episodeBody}"`，而 episodeBody 是
 * 写给老师看的第二人称剧情简介（「你需要帮她补齐推导」「你先听他讲」），
 * 拼起来就成了旁白念稿：气泡里出现「我是李想。李想想自己讲明白……你先听他讲」。
 * 现在开场走 `openingLine`，episodeBody 只留给世界树/创建界面的剧情展示。
 */
class FormalPresetOpeningVoiceTest {

    /** 旁白/指令口吻标记：这些出现在开场白里就说明又拿剧情简介当台词了。 */
    private val narratorMarkers = listOf("你需要", "你要先", "你要解释", "你先听他讲", "你需要帮", "想听你把")

    @Test
    fun everyBuiltInPresetOpensInLearnerVoice() {
        assertTrue("内置预设不能为空", FormalLearningPresets.all.isNotEmpty())
        for (preset in FormalLearningPresets.all) {
            assertTrue("预设 ${preset.id} 缺少学生第一人称 openingLine", preset.openingLine.isNotBlank())

            val opening = NewSessionConfiguration.fromPreset(preset).openingMessage
            assertEquals("开场消息应直接采用 openingLine", preset.openingLine, opening)

            assertFalse(
                "开场白不应是「我是X。」式自我介绍（气泡上方已显示说话人）",
                opening.startsWith("我是")
            )
            narratorMarkers.forEach { marker ->
                assertFalse(
                    "预设 ${preset.id} 开场白仍带旁白口吻「$marker」：$opening",
                    opening.contains(marker)
                )
            }
            assertFalse(
                "预设 ${preset.id} 开场白不应照搬剧情简介",
                opening.contains(preset.episodeBody)
            )
        }
    }

    @Test
    fun worldTreeStoryStillUsesEpisodeBody() {
        // episodeBody 的角色不变：世界树/创建界面继续展示剧情简介。
        val preset = FormalLearningPresets.byId("reverse-tutor")
        assertTrue(preset != null)
        val story = NewSessionConfiguration.fromPreset(preset!!).story
        assertTrue("世界树仍应是剧情简介", story.contains(preset.episodeBody))
    }
}
