package com.reversetutor.preview.shell

import android.net.Uri
import com.reversetutor.core.data.llm.ChatGenerationRepository
import com.reversetutor.core.data.llm.SourceVisionOutcome
import com.reversetutor.core.model.MessageAttachment

/**
 * 图片资料云端多模态转写（V1-019 知识锚点第二期：NEWMP-V1-020）。
 *
 * 当本地 OCR 在图片上读不到任何文字（纯几何图、函数图像、化学结构式等），
 * 且用户打开了「云端看图转写」开关时，把图片发给所配置渠道的多模态模型，
 * 用转写文本替代文字层，走与普通文本资料完全相同的切片 / 检索链路。
 * 转写失败或未配置模型时返回 null，上层维持原有的 FutureAssisted 标记。
 */
internal suspend fun describeSourceImageWithVision(
    repository: ChatGenerationRepository,
    sessionId: String,
    fileName: String,
    mimeType: String?,
    uri: String,
    visionModelName: String
): String? {
    val attachment = MessageAttachment(
        id = "vision-source-" + System.currentTimeMillis(),
        spaceId = "",
        messageId = "",
        name = fileName,
        mimeType = mimeType ?: "image/*",
        uri = uri
    )
    return try {
        when (
            val outcome = repository.describeImageForSource(
                sessionId = sessionId,
                image = attachment,
                visionModelName = visionModelName
            )
        ) {
            is SourceVisionOutcome.Generated ->
                outcome.descriptionText.trim().takeIf { it.isNotEmpty() }
            is SourceVisionOutcome.ProviderFailed -> null
            SourceVisionOutcome.NoModelConfigured -> null
            SourceVisionOutcome.UnsupportedVision -> null
            SourceVisionOutcome.BlankPrompt -> null
        }
    } catch (t: Throwable) {
        null
    }
}
