package com.reversetutor.core.domain

data class ToolJsonSchema(
    val type: String = "object",
    val required: List<String> = emptyList(),
    val maxBytes: Int = 2_000
)

data class ToolPresentationIntent(val kind: String = "none", val targetId: String? = null)

data class SessionToolDefinition(
    val name: String,
    val inputSchema: ToolJsonSchema,
    val outputSchema: ToolJsonSchema,
    val presentation: ToolPresentationIntent
)

data class SessionToolCall(
    val callId: String,
    val name: String,
    val sessionId: String,
    val argumentsJson: String
)

sealed interface ToolSafeResult {
    data class Document(val documentId: String) : ToolSafeResult
    data class Table(val tableId: String, val rowCount: Int = 0) : ToolSafeResult
    data class ReferenceOpenTarget(val sourceId: String) : ToolSafeResult
    data class Rejected(val code: String) : ToolSafeResult
}

data class SessionToolResult(
    val callId: String,
    val status: String,
    val safeResult: ToolSafeResult,
    val presentation: ToolPresentationIntent
)

data class SessionToolAuthorization(val allowed: Boolean, val code: String)
