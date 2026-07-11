package com.reversetutor.core.protocol

enum class ProtocolDocumentType(val wireType: String) {
    LegacyExport("legacy_export"),
    FullBackup("full_backup"),
    SessionExport("session_export"),
    GraphSnapshot("graph_snapshot"),
    Preset("preset"),
    LlmProfile("llm_profile"),
    ImportResult("import_result");

    companion object {
        fun fromWireType(wireType: String?): ProtocolDocumentType? =
            entries.firstOrNull { it.wireType == wireType }
    }
}

data class VersionedProtocolContract(
    val schema: String,
    val version: Int,
    val documentType: ProtocolDocumentType,
    val requiredFields: List<String>,
    val allowedFields: Set<String>,
    val expectedFieldKinds: Map<String, ProtocolFieldKind>
)

enum class ProtocolFieldKind(val label: String) {
    Text("string"),
    Number("number"),
    Object("object"),
    Array("array")
}

data class ProtocolValidationResult(
    val schema: String?,
    val version: Int?,
    val documentType: ProtocolDocumentType?,
    val errors: List<String>,
    val warnings: List<String>,
    val redactedJson: String
) {
    val isValid: Boolean = errors.isEmpty()
}

object VersionedProtocolSchemas {
    private val commonExpectedKinds = mapOf(
        "schema" to ProtocolFieldKind.Text,
        "version" to ProtocolFieldKind.Number,
        "type" to ProtocolFieldKind.Text
    )

    private val commonFields = setOf("schema", "version", "type")

    val all: List<VersionedProtocolContract> = listOf(
        VersionedProtocolContract(
            schema = ProtocolModule.legacyExportSchema,
            version = 1,
            documentType = ProtocolDocumentType.LegacyExport,
            requiredFields = listOf("schema", "version", "type", "created_at", "payload"),
            allowedFields = commonFields + setOf("created_at", "payload"),
            expectedFieldKinds = commonExpectedKinds + mapOf(
                "created_at" to ProtocolFieldKind.Text,
                "payload" to ProtocolFieldKind.Object
            )
        ),
        VersionedProtocolContract(
            schema = ProtocolModule.fullBackupSchema,
            version = 1,
            documentType = ProtocolDocumentType.FullBackup,
            requiredFields = listOf("schema", "version", "type", "created_at", "sessions", "llm_profiles", "graph"),
            allowedFields = commonFields + setOf("created_at", "sessions", "llm_profiles", "graph"),
            expectedFieldKinds = commonExpectedKinds + mapOf(
                "created_at" to ProtocolFieldKind.Text,
                "sessions" to ProtocolFieldKind.Array,
                "llm_profiles" to ProtocolFieldKind.Array,
                "graph" to ProtocolFieldKind.Object
            )
        ),
        VersionedProtocolContract(
            schema = ProtocolModule.sessionExportSchema,
            version = 1,
            documentType = ProtocolDocumentType.SessionExport,
            requiredFields = listOf("schema", "version", "type", "created_at", "session", "messages"),
            allowedFields = commonFields + setOf("created_at", "session", "messages"),
            expectedFieldKinds = commonExpectedKinds + mapOf(
                "created_at" to ProtocolFieldKind.Text,
                "session" to ProtocolFieldKind.Object,
                "messages" to ProtocolFieldKind.Array
            )
        ),
        VersionedProtocolContract(
            schema = ProtocolModule.graphSnapshotSchema,
            version = 1,
            documentType = ProtocolDocumentType.GraphSnapshot,
            requiredFields = listOf("schema", "version", "type", "created_at", "nodes", "edges"),
            allowedFields = commonFields + setOf("created_at", "nodes", "edges"),
            expectedFieldKinds = commonExpectedKinds + mapOf(
                "created_at" to ProtocolFieldKind.Text,
                "nodes" to ProtocolFieldKind.Array,
                "edges" to ProtocolFieldKind.Array
            )
        ),
        VersionedProtocolContract(
            schema = ProtocolModule.presetSchema,
            version = 1,
            documentType = ProtocolDocumentType.Preset,
            requiredFields = listOf("schema", "version", "type", "title", "role", "goal", "profile"),
            allowedFields = commonFields + setOf("title", "role", "goal", "profile", "sourceHandoff"),
            expectedFieldKinds = commonExpectedKinds + mapOf(
                "title" to ProtocolFieldKind.Text,
                "role" to ProtocolFieldKind.Text,
                "goal" to ProtocolFieldKind.Text,
                "profile" to ProtocolFieldKind.Text,
                "sourceHandoff" to ProtocolFieldKind.Text
            )
        ),
        VersionedProtocolContract(
            schema = ProtocolModule.llmProfileSchema,
            version = 1,
            documentType = ProtocolDocumentType.LlmProfile,
            requiredFields = listOf(
                "schema",
                "version",
                "type",
                "id",
                "name",
                "provider",
                "api_type",
                "model",
                "secret_status"
            ),
            allowedFields = commonFields + setOf(
                "id",
                "name",
                "provider",
                "api_type",
                "base_url",
                "model",
                "secret_status",
                "capabilities"
            ),
            expectedFieldKinds = commonExpectedKinds + mapOf(
                "id" to ProtocolFieldKind.Text,
                "name" to ProtocolFieldKind.Text,
                "provider" to ProtocolFieldKind.Text,
                "api_type" to ProtocolFieldKind.Text,
                "base_url" to ProtocolFieldKind.Text,
                "model" to ProtocolFieldKind.Text,
                "secret_status" to ProtocolFieldKind.Text,
                "capabilities" to ProtocolFieldKind.Object
            )
        ),
        VersionedProtocolContract(
            schema = ProtocolModule.importResultSchema,
            version = 1,
            documentType = ProtocolDocumentType.ImportResult,
            requiredFields = listOf(
                "schema",
                "version",
                "type",
                "status",
                "mode",
                "inserted_counts",
                "skipped_counts",
                "warnings",
                "errors"
            ),
            allowedFields = commonFields + setOf(
                "status",
                "mode",
                "inserted_counts",
                "skipped_counts",
                "warnings",
                "errors"
            ),
            expectedFieldKinds = commonExpectedKinds + mapOf(
                "status" to ProtocolFieldKind.Text,
                "mode" to ProtocolFieldKind.Text,
                "inserted_counts" to ProtocolFieldKind.Object,
                "skipped_counts" to ProtocolFieldKind.Object,
                "warnings" to ProtocolFieldKind.Array,
                "errors" to ProtocolFieldKind.Array
            )
        ),
        VersionedProtocolContract(
            schema = ProtocolModule.fullBackupSchema,
            version = 2,
            documentType = ProtocolDocumentType.FullBackup,
            requiredFields = listOf(
                "schema", "version", "type", "created_at", "sessions",
                "provider_connections", "model_bindings", "graph"
            ),
            allowedFields = commonFields + setOf(
                "created_at", "sessions", "provider_connections", "model_bindings", "graph"
            ),
            expectedFieldKinds = commonExpectedKinds + mapOf(
                "created_at" to ProtocolFieldKind.Text,
                "sessions" to ProtocolFieldKind.Array,
                "provider_connections" to ProtocolFieldKind.Array,
                "model_bindings" to ProtocolFieldKind.Array,
                "graph" to ProtocolFieldKind.Object
            )
        ),
        VersionedProtocolContract(
            schema = ProtocolModule.sessionExportSchema,
            version = 2,
            documentType = ProtocolDocumentType.SessionExport,
            requiredFields = listOf("schema", "version", "type", "created_at", "session", "messages"),
            allowedFields = commonFields + setOf("created_at", "session", "messages"),
            expectedFieldKinds = commonExpectedKinds + mapOf(
                "created_at" to ProtocolFieldKind.Text,
                "session" to ProtocolFieldKind.Object,
                "messages" to ProtocolFieldKind.Array
            )
        ),
        VersionedProtocolContract(
            schema = ProtocolModule.graphSnapshotSchema,
            version = 2,
            documentType = ProtocolDocumentType.GraphSnapshot,
            requiredFields = listOf("schema", "version", "type", "created_at", "nodes", "edges"),
            allowedFields = commonFields + setOf("created_at", "nodes", "edges"),
            expectedFieldKinds = commonExpectedKinds + mapOf(
                "created_at" to ProtocolFieldKind.Text,
                "nodes" to ProtocolFieldKind.Array,
                "edges" to ProtocolFieldKind.Array
            )
        ),
        VersionedProtocolContract(
            schema = ProtocolModule.presetSchema,
            version = 2,
            documentType = ProtocolDocumentType.Preset,
            requiredFields = listOf("schema", "version", "type", "title", "role", "goal", "profile"),
            allowedFields = commonFields + setOf("title", "role", "goal", "profile", "sourceHandoff"),
            expectedFieldKinds = commonExpectedKinds + mapOf(
                "title" to ProtocolFieldKind.Text,
                "role" to ProtocolFieldKind.Text,
                "goal" to ProtocolFieldKind.Text,
                "profile" to ProtocolFieldKind.Text,
                "sourceHandoff" to ProtocolFieldKind.Text
            )
        )
    )

    private val bySchemaAndVersion: Map<Pair<String, Int>, VersionedProtocolContract> =
        all.associateBy { it.schema to it.version }

    fun forSchema(schema: String?, version: Int?): VersionedProtocolContract? =
        if (schema == null || version == null) null else bySchemaAndVersion[schema to version]

    fun supportsSchema(schema: String?): Boolean =
        schema != null && all.any { it.schema == schema }

    fun baselineForSchema(schema: String?): VersionedProtocolContract? =
        all.filter { it.schema == schema }.minByOrNull { it.version }
}

object VersionedProtocolValidator {
    fun validate(json: String): ProtocolValidationResult {
        val parsed = try {
            LightweightJsonParser.parse(json)
        } catch (error: JsonParseException) {
            return ProtocolValidationResult(
                schema = null,
                version = null,
                documentType = null,
                errors = listOf("JSON parse error: ${error.message}"),
                warnings = emptyList(),
                redactedJson = ProtocolSecretPolicy.redactRaw(json)
            )
        }

        val redactedJson = ProtocolSecretPolicy.redact(parsed).toCanonicalJson()
        val root = parsed as? JsonObject
            ?: return ProtocolValidationResult(
                schema = null,
                version = null,
                documentType = null,
                errors = listOf("Protocol JSON must be an object."),
                warnings = emptyList(),
                redactedJson = redactedJson
            )

        val schema = root.stringField("schema")
        val version = root.intField("version")
        val wireType = root.stringField("type")
        val contract = VersionedProtocolSchemas.forSchema(schema, version)
            ?: if (version == null) VersionedProtocolSchemas.baselineForSchema(schema) else null
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (schema.isNullOrBlank()) {
            errors += "Field schema is required."
        } else if (!VersionedProtocolSchemas.supportsSchema(schema)) {
            errors += "Unsupported schema: $schema."
        } else if (version == null) {
            errors += "Field version is required."
        } else if (contract == null) {
            errors += "Unsupported version $version for schema $schema."
        }

        val documentType = contract?.documentType ?: ProtocolDocumentType.fromWireType(wireType)

        if (contract != null) {
            validateRequiredFields(root, contract, errors)
            validateFieldKinds(root, contract, errors)
            validateVersionAndType(version, wireType, contract, errors)
            validateUnknownFields(root, contract, warnings)
            validateDocumentSpecificRules(root, contract.documentType, errors, warnings)
        }

        if (ProtocolSecretPolicy.containsSecretMaterial(root)) {
            errors += secretErrorFor(documentType)
        }

        return ProtocolValidationResult(
            schema = schema,
            version = version,
            documentType = documentType,
            errors = errors.distinct(),
            warnings = warnings.distinct(),
            redactedJson = redactedJson
        )
    }

    private fun validateRequiredFields(
        root: JsonObject,
        contract: VersionedProtocolContract,
        errors: MutableList<String>
    ) {
        contract.requiredFields.forEach { field ->
            if (!root.hasField(field)) {
                errors += "Field $field is required."
            }
        }
    }

    private fun validateFieldKinds(
        root: JsonObject,
        contract: VersionedProtocolContract,
        errors: MutableList<String>
    ) {
        contract.expectedFieldKinds.forEach { (field, expectedKind) ->
            val value = root.fields[field] ?: return@forEach
            if (!value.matchesKind(expectedKind)) {
                errors += "Field $field must be a ${expectedKind.label}."
            }
        }
    }

    private fun validateVersionAndType(
        version: Int?,
        wireType: String?,
        contract: VersionedProtocolContract,
        errors: MutableList<String>
    ) {
        if (version != contract.version) {
            errors += "Schema ${contract.schema} requires version ${contract.version}."
        }
        if (wireType != contract.documentType.wireType) {
            errors += "Schema ${contract.schema} requires type ${contract.documentType.wireType}."
        }
    }

    private fun validateUnknownFields(
        root: JsonObject,
        contract: VersionedProtocolContract,
        warnings: MutableList<String>
    ) {
        val unknownFields = root.fields.keys
            .filterNot { it in contract.allowedFields }
            .sorted()
        if (unknownFields.isNotEmpty()) {
            warnings += "Unknown top-level fields for ${contract.schema}: ${unknownFields.joinToString(", ")}."
        }
    }

    private fun validateDocumentSpecificRules(
        root: JsonObject,
        documentType: ProtocolDocumentType,
        errors: MutableList<String>,
        warnings: MutableList<String>
    ) {
        when (documentType) {
            ProtocolDocumentType.SessionExport -> {
                val messages = root.fields["messages"] as? JsonArray
                if (messages != null && messages.values.isEmpty()) {
                    warnings += "Session export contains no messages."
                }
            }
            ProtocolDocumentType.Preset -> {
                listOf("title", "role", "goal", "profile").forEach { field ->
                    if (root.stringField(field).isNullOrBlank()) {
                        errors += "Field $field must not be blank."
                    }
                }
            }
            ProtocolDocumentType.LlmProfile -> {
                val secretStatus = root.stringField("secret_status")
                if (secretStatus != null && secretStatus !in setOf("excluded", "redacted", "none")) {
                    errors += "LLM profile secret_status must be excluded, redacted, or none."
                }
            }
            ProtocolDocumentType.ImportResult -> {
                val status = root.stringField("status")
                if (status != null && status !in setOf("completed", "failed", "partial", "dry_run")) {
                    errors += "Import result status is not supported: $status."
                }
                val mode = root.stringField("mode")
                if (mode != null && mode !in setOf("append", "overwrite", "new_space")) {
                    errors += "Import result mode is not supported: $mode."
                }
            }
            ProtocolDocumentType.LegacyExport,
            ProtocolDocumentType.FullBackup,
            ProtocolDocumentType.GraphSnapshot -> Unit
        }
    }

    private fun JsonValue.matchesKind(kind: ProtocolFieldKind): Boolean =
        when (kind) {
            ProtocolFieldKind.Text -> this is JsonString
            ProtocolFieldKind.Number -> this is JsonNumber
            ProtocolFieldKind.Object -> this is JsonObject
            ProtocolFieldKind.Array -> this is JsonArray
        }

    private fun secretErrorFor(documentType: ProtocolDocumentType?): String =
        when (documentType) {
            ProtocolDocumentType.Preset -> "Preset protocol must not include secret or key material."
            ProtocolDocumentType.LlmProfile -> "LLM profile protocol must not include secret or key material."
            else -> "Protocol document must not include secret or key material."
        }
}

internal object ProtocolSecretPolicy {
    private const val redactedValue = "[REDACTED]"

    private val secretValuePatterns = listOf(
        Regex("sk-[A-Za-z0-9_\\-]{3,}"),
        Regex("(?i)bearer\\s+[A-Za-z0-9._\\-]+"),
        Regex("(?i)(api[_-]?key|secret|token|password)\\s*[:=]")
    )
    private val safeSecretMetadataKeys = setOf("secret_status", "secretStatus")

    fun containsSecretMaterial(value: JsonValue): Boolean =
        when (value) {
            is JsonObject -> value.fields.any { (key, child) ->
                key.looksLikeSecretKey() || containsSecretMaterial(child)
            }
            is JsonArray -> value.values.any { containsSecretMaterial(it) }
            is JsonString -> value.value.looksLikeSecretValue()
            is JsonBoolean,
            is JsonNumber,
            JsonNull -> false
        }

    fun redact(value: JsonValue): JsonValue =
        when (value) {
            is JsonObject -> {
                val redactedFields = linkedMapOf<String, JsonValue>()
                value.fields.forEach { (key, child) ->
                    redactedFields[key] = if (key.looksLikeSecretKey()) {
                        JsonString(redactedValue)
                    } else {
                        redact(child)
                    }
                }
                JsonObject(redactedFields)
            }
            is JsonArray -> JsonArray(value.values.map { redact(it) })
            is JsonString -> if (value.value.looksLikeSecretValue()) JsonString(redactedValue) else value
            is JsonBoolean,
            is JsonNumber,
            JsonNull -> value
        }

    fun redactRaw(json: String): String =
        secretValuePatterns.fold(json) { current, pattern ->
            pattern.replace(current, redactedValue)
        }

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
}
