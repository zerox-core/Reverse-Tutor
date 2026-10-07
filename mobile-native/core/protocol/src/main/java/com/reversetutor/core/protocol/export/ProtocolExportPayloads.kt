package com.reversetutor.core.protocol.export

import com.reversetutor.core.protocol.JsonArray
import com.reversetutor.core.protocol.JsonBoolean
import com.reversetutor.core.protocol.JsonNull
import com.reversetutor.core.protocol.JsonNumber
import com.reversetutor.core.protocol.JsonObject
import com.reversetutor.core.protocol.JsonString
import com.reversetutor.core.protocol.JsonValue
import com.reversetutor.core.protocol.ProtocolDocumentType
import com.reversetutor.core.protocol.ProtocolModule
import com.reversetutor.core.protocol.ProtocolValidationResult
import com.reversetutor.core.protocol.VersionedProtocolValidator

data class ProtocolExportPayload(
    val documentType: ProtocolDocumentType,
    val json: String
)

sealed interface ExportFieldValue {
    data class StringValue(val value: String) : ExportFieldValue
    data class NumberValue(val raw: String) : ExportFieldValue {
        init {
            require(raw.matches(jsonNumberPattern)) { "NumberValue raw must be a valid JSON number." }
        }
    }
    data class BooleanValue(val value: Boolean) : ExportFieldValue
    data class ObjectValue(val fields: Map<String, ExportFieldValue>) : ExportFieldValue
    data class ArrayValue(val values: List<ExportFieldValue>) : ExportFieldValue
    object NullValue : ExportFieldValue
}

data class ExportSessionRecord(
    val id: String,
    val title: String? = null,
    val createdAt: String? = null,
    val extraFields: Map<String, ExportFieldValue> = emptyMap()
)

data class ExportMessageRecord(
    val id: String,
    val role: String,
    val text: String,
    val createdAt: String? = null,
    val extraFields: Map<String, ExportFieldValue> = emptyMap()
)

data class ExportLlmProfileRecord(
    val id: String,
    val name: String,
    val provider: String,
    val apiType: String,
    val model: String,
    val baseUrl: String? = null,
    val capabilities: Map<String, Boolean> = emptyMap(),
    val secretStatus: ExportSecretStatus = ExportSecretStatus.Excluded,
    // Accepted from native profile records, but intentionally never serialized into export JSON.
    val secretRef: String? = null,
    val apiKey: String? = null,
    val extraFields: Map<String, ExportFieldValue> = emptyMap()
)

data class ExportProviderConnectionRecord(
    val id: String,
    val name: String,
    val protocol: String,
    val providerName: String? = null,
    val baseUrl: String? = null,
    val secretStatus: ExportSecretStatus = ExportSecretStatus.Excluded
)

data class ExportModelBindingRecord(
    val id: String,
    val connectionId: String,
    val modelId: String,
    val displayName: String,
    val enabled: Boolean = true
)

enum class ExportSecretStatus(val wireValue: String) {
    Excluded("excluded"),
    Redacted("redacted"),
    None("none")
}

data class ExportGraphSnapshotPayload(
    val createdAt: String,
    val nodes: List<ExportGraphNodeRecord>,
    val edges: List<ExportGraphEdgeRecord>
)

data class ExportGraphNodeRecord(
    val id: String,
    val kind: String,
    val title: String? = null,
    val extraFields: Map<String, ExportFieldValue> = emptyMap()
)

data class ExportGraphEdgeRecord(
    val id: String,
    val sourceNodeId: String,
    val targetNodeId: String,
    val kind: String,
    val extraFields: Map<String, ExportFieldValue> = emptyMap()
)

data class ExportPresetPayload(
    val title: String,
    val role: String,
    val goal: String,
    val profile: String,
    val sourceHandoff: String = "deferred",
    val extraFields: Map<String, ExportFieldValue> = emptyMap()
)

object ProtocolExportPayloadBuilder {
    fun currentSession(
        createdAt: String,
        session: ExportSessionRecord,
        messages: List<ExportMessageRecord>
    ): ProtocolExportPayload =
        ProtocolExportPayload(
            documentType = ProtocolDocumentType.SessionExport,
            json = JsonObject(
                linkedMapOf(
                    "schema" to JsonString(ProtocolModule.sessionExportSchema),
                    "version" to JsonNumber(currentVersion),
                    "type" to JsonString(ProtocolDocumentType.SessionExport.wireType),
                    "created_at" to safeString(createdAt),
                    "session" to session.toJsonObject(),
                    "messages" to JsonArray(messages.map { it.toJsonObject() })
                )
            ).toCanonicalJson()
        )

    fun fullBackup(
        createdAt: String,
        sessions: List<ExportSessionRecord>,
        llmProfiles: List<ExportLlmProfileRecord>,
        graph: ExportGraphSnapshotPayload,
        providerConnections: List<ExportProviderConnectionRecord> = emptyList(),
        modelBindings: List<ExportModelBindingRecord> = emptyList()
    ): ProtocolExportPayload =
        ProtocolExportPayload(
            documentType = ProtocolDocumentType.FullBackup,
            json = JsonObject(
                linkedMapOf(
                    "schema" to JsonString(ProtocolModule.fullBackupSchema),
                    "version" to JsonNumber(currentVersion),
                    "type" to JsonString(ProtocolDocumentType.FullBackup.wireType),
                    "created_at" to safeString(createdAt),
                    "sessions" to JsonArray(sessions.map { it.toJsonObject() }),
                    "provider_connections" to JsonArray(
                        (providerConnections.ifEmpty {
                            llmProfiles.map { profile ->
                                ExportProviderConnectionRecord(
                                    id = "connection-${profile.id}",
                                    name = profile.name,
                                    protocol = profile.apiType,
                                    providerName = profile.provider,
                                    baseUrl = profile.baseUrl,
                                    secretStatus = profile.secretStatus
                                )
                            }
                        }).map { it.toJsonObject() }
                    ),
                    "model_bindings" to JsonArray(
                        (modelBindings.ifEmpty {
                            llmProfiles.map { profile ->
                            ExportModelBindingRecord(
                                id = profile.id,
                                connectionId = "connection-${profile.id}",
                                modelId = profile.model,
                                displayName = profile.name,
                                enabled = profile.capabilities["enabled"] ?: true
                            )
                            }
                        }).map { it.toJsonObject() }
                    ),
                    "graph" to graph.toJsonObject()
                )
            ).toCanonicalJson()
        )

    fun graphSnapshot(snapshot: ExportGraphSnapshotPayload): ProtocolExportPayload =
        ProtocolExportPayload(
            documentType = ProtocolDocumentType.GraphSnapshot,
            json = snapshot.toJsonObject().toCanonicalJson()
        )

    fun preset(preset: ExportPresetPayload): ProtocolExportPayload =
        ProtocolExportPayload(
            documentType = ProtocolDocumentType.Preset,
            json = preset.toJsonObject().toCanonicalJson()
        )
}

object ProtocolExportPayloadValidator {
    private val exportDocumentTypes = setOf(
        ProtocolDocumentType.FullBackup,
        ProtocolDocumentType.SessionExport,
        ProtocolDocumentType.GraphSnapshot,
        ProtocolDocumentType.Preset
    )

    fun validate(payload: ProtocolExportPayload): ProtocolValidationResult =
        validate(payload.json)

    fun validate(json: String): ProtocolValidationResult {
        val result = VersionedProtocolValidator.validate(json)
        if (result.documentType in exportDocumentTypes) {
            return result
        }
        return result.copy(
            errors = result.errors + "Protocol document type must be an export payload."
        )
    }
}

private const val currentVersion = "2"
private const val redactedValue = "[REDACTED]"
private val jsonNumberPattern = Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")
private val safeSecretMetadataKeys = setOf("secret_status", "secretStatus")
private val secretValuePatterns = listOf(
    Regex("sk-[A-Za-z0-9_\\-]{3,}"),
    Regex("(?i)bearer\\s+[A-Za-z0-9._\\-]+"),
    Regex("(?i)(api[_-]?key|secret|token|password)\\s*[:=]")
)

private fun ExportSessionRecord.toJsonObject(): JsonObject {
    val fields = linkedMapOf<String, JsonValue>("id" to safeString(id))
    title?.let { fields["title"] = safeString(it) }
    createdAt?.let { fields["created_at"] = safeString(it) }
    fields.appendSafeExtras(extraFields)
    return JsonObject(fields)
}

private fun ExportMessageRecord.toJsonObject(): JsonObject {
    val fields = linkedMapOf<String, JsonValue>(
        "id" to safeString(id),
        "role" to safeString(role),
        "text" to safeString(text)
    )
    createdAt?.let { fields["created_at"] = safeString(it) }
    fields.appendSafeExtras(extraFields)
    return JsonObject(fields)
}

private fun ExportLlmProfileRecord.toJsonObject(): JsonObject {
    val fields = linkedMapOf<String, JsonValue>(
        "schema" to JsonString(ProtocolModule.llmProfileSchema),
        "version" to JsonNumber(currentVersion),
        "type" to JsonString(ProtocolDocumentType.LlmProfile.wireType),
        "id" to safeString(id),
        "name" to safeString(name),
        "provider" to safeString(provider),
        "api_type" to safeString(apiType)
    )
    baseUrl?.let { fields["base_url"] = safeString(it) }
    fields["model"] = safeString(model)
    fields["secret_status"] = JsonString(secretStatus.wireValue)
    if (capabilities.isNotEmpty()) {
        fields["capabilities"] = JsonObject(
            capabilities.entries
                .filterNot { (key, _) -> key.looksLikeSecretKey() }
                .associateTo(linkedMapOf()) { (key, value) -> key to JsonBoolean(value) }
        )
    }
    fields.appendSafeExtras(extraFields)
    return JsonObject(fields)
}

private fun ExportProviderConnectionRecord.toJsonObject(): JsonObject {
    val fields = linkedMapOf<String, JsonValue>(
        "id" to safeString(id),
        "name" to safeString(name),
        "protocol" to safeString(protocol),
        "secret_status" to JsonString(secretStatus.wireValue)
    )
    providerName?.let { fields["provider_name"] = safeString(it) }
    baseUrl?.let { fields["base_url"] = safeString(it) }
    return JsonObject(fields)
}

private fun ExportModelBindingRecord.toJsonObject(): JsonObject =
    JsonObject(
        linkedMapOf(
            "id" to safeString(id),
            "connection_id" to safeString(connectionId),
            "model_id" to safeString(modelId),
            "display_name" to safeString(displayName),
            "enabled" to JsonBoolean(enabled)
        )
    )

private fun ExportGraphSnapshotPayload.toJsonObject(): JsonObject =
    JsonObject(
        linkedMapOf(
            "schema" to JsonString(ProtocolModule.graphSnapshotSchema),
            "version" to JsonNumber(currentVersion),
            "type" to JsonString(ProtocolDocumentType.GraphSnapshot.wireType),
            "created_at" to safeString(createdAt),
            "nodes" to JsonArray(nodes.map { it.toJsonObject() }),
            "edges" to JsonArray(edges.map { it.toJsonObject() })
        )
    )

private fun ExportGraphNodeRecord.toJsonObject(): JsonObject {
    val fields = linkedMapOf<String, JsonValue>(
        "id" to safeString(id),
        "kind" to safeString(kind)
    )
    title?.let { fields["title"] = safeString(it) }
    fields.appendSafeExtras(extraFields)
    return JsonObject(fields)
}

private fun ExportGraphEdgeRecord.toJsonObject(): JsonObject {
    val fields = linkedMapOf<String, JsonValue>(
        "id" to safeString(id),
        "source_node_id" to safeString(sourceNodeId),
        "target_node_id" to safeString(targetNodeId),
        "kind" to safeString(kind)
    )
    fields.appendSafeExtras(extraFields)
    return JsonObject(fields)
}

private fun ExportPresetPayload.toJsonObject(): JsonObject {
    val fields = linkedMapOf<String, JsonValue>(
        "schema" to JsonString(ProtocolModule.presetSchema),
        "version" to JsonNumber(currentVersion),
        "type" to JsonString(ProtocolDocumentType.Preset.wireType),
        "title" to safeString(title),
        "role" to safeString(role),
        "goal" to safeString(goal),
        "profile" to safeString(profile),
        "sourceHandoff" to safeString(sourceHandoff)
    )
    fields.appendSafeExtras(extraFields)
    return JsonObject(fields)
}

private fun LinkedHashMap<String, JsonValue>.appendSafeExtras(extraFields: Map<String, ExportFieldValue>) {
    extraFields.forEach { (key, value) ->
        if (!containsKey(key) && !key.looksLikeSecretKey()) {
            this[key] = value.toSafeJsonValue()
        }
    }
}

private fun ExportFieldValue.toSafeJsonValue(): JsonValue =
    when (this) {
        is ExportFieldValue.StringValue -> safeString(value)
        is ExportFieldValue.NumberValue -> JsonNumber(raw)
        is ExportFieldValue.BooleanValue -> JsonBoolean(value)
        is ExportFieldValue.ObjectValue -> JsonObject(
            fields.entries
                .filterNot { (key, _) -> key.looksLikeSecretKey() }
                .associateTo(linkedMapOf()) { (key, value) -> key to value.toSafeJsonValue() }
        )
        is ExportFieldValue.ArrayValue -> JsonArray(values.map { it.toSafeJsonValue() })
        ExportFieldValue.NullValue -> JsonNull
    }

private fun safeString(value: String): JsonString =
    JsonString(if (value.looksLikeSecretValue()) redactedValue else value)

private fun String.looksLikeSecretKey(): Boolean {
    if (this in safeSecretMetadataKeys) {
        return false
    }
    val normalized = lowercase().replace("-", "").replace("_", "")
    return normalized == "key" ||
        normalized.endsWith("key") ||
        normalized.contains("secret") ||
        normalized.contains("token") ||
        normalized.contains("password") ||
        normalized.contains("credential") ||
        normalized.contains("authorization")
}

private fun String.looksLikeSecretValue(): Boolean =
    secretValuePatterns.any { pattern -> pattern.containsMatchIn(this) }
