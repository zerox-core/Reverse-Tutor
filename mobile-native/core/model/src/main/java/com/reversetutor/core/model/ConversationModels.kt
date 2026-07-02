package com.reversetutor.core.model

data class TutorSession(
    val id: String,
    val spaceId: String,
    val title: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val llmProfileId: String? = null,
    val settingsId: String? = null,
    val sourceImportId: String? = null
)

data class Message(
    val id: String,
    val spaceId: String,
    val sessionId: String,
    val role: MessageRole,
    val text: String,
    val createdAtEpochMillis: Long,
    val parentMessageId: String? = null,
    val sourceImportId: String? = null
)

enum class MessageRole {
    User,
    Assistant,
    System,
    Tool
}

data class MessageAttachment(
    val id: String,
    val spaceId: String,
    val messageId: String,
    val name: String,
    val mimeType: String? = null,
    val uri: String? = null,
    val sourceId: String? = null
)

data class MessageQuote(
    val id: String,
    val spaceId: String,
    val messageId: String,
    val quotedMessageId: String,
    val excerpt: String
)

data class SessionSettings(
    val id: String,
    val spaceId: String,
    val sessionId: String,
    val llmProfileId: String? = null,
    val systemPrompt: String? = null
)
