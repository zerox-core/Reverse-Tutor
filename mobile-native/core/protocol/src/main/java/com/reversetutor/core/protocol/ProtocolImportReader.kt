package com.reversetutor.core.protocol

data class ProtocolImportReadResult(
    val document: ProtocolImportDocument?,
    val validation: ProtocolValidationResult,
    val warnings: List<String> = emptyList()
) {
    val isValid: Boolean = document != null && validation.isValid
}

data class ProtocolImportDocument(
    val sourceSchema: String,
    val version: Int,
    val documentType: ProtocolDocumentType,
    val sessions: List<ProtocolImportSessionRecord> = emptyList(),
    val messages: List<ProtocolImportMessageRecord> = emptyList(),
    val llmProfiles: List<ProtocolImportLlmProfileRecord> = emptyList(),
    val providerConnections: List<ProtocolImportProviderConnectionRecord> = emptyList(),
    val modelBindings: List<ProtocolImportModelBindingRecord> = emptyList(),
    val warnings: List<String> = emptyList()
)

data class ProtocolImportSessionRecord(
    val id: String,
    val title: String,
    val systemPrompt: String? = null,
    val modelBindingId: String? = null
)

data class ProtocolImportMessageRecord(
    val id: String,
    val sessionId: String?,
    val role: String,
    val text: String
)

data class ProtocolImportLlmProfileRecord(
    val id: String,
    val name: String,
    val provider: String,
    val model: String,
    val baseUrl: String? = null
)

data class ProtocolImportProviderConnectionRecord(
    val id: String,
    val name: String,
    val protocol: String,
    val providerName: String? = null,
    val baseUrl: String? = null
)

data class ProtocolImportModelBindingRecord(
    val id: String,
    val connectionId: String,
    val modelId: String,
    val displayName: String? = null,
    val enabled: Boolean = true
)

object ProtocolImportReader {
    fun read(json: String): ProtocolImportReadResult {
        val validation = VersionedProtocolValidator.validate(json)
        if (!validation.isValid) {
            return ProtocolImportReadResult(
                document = null,
                validation = validation,
                warnings = validation.warnings
            )
        }

        val root = LightweightJsonParser.parse(json) as? JsonObject
            ?: return ProtocolImportReadResult(
                document = null,
                validation = validation.copy(errors = validation.errors + "Protocol JSON must be an object."),
                warnings = validation.warnings
            )

        val documentType = validation.documentType
            ?: return ProtocolImportReadResult(
                document = null,
                validation = validation.copy(errors = validation.errors + "Protocol document type is required."),
                warnings = validation.warnings
            )

        val document = when (documentType) {
            ProtocolDocumentType.FullBackup -> readFullBackup(root, validation, documentType)
            ProtocolDocumentType.SessionExport -> readSessionExport(root, validation, documentType)
            ProtocolDocumentType.Preset -> readPreset(root, validation, documentType)
            ProtocolDocumentType.LlmProfile -> readLlmProfile(root, validation, documentType)
            ProtocolDocumentType.LegacyExport -> readLegacyExport(root, validation, documentType)
            ProtocolDocumentType.GraphSnapshot,
            ProtocolDocumentType.ImportResult -> ProtocolImportDocument(
                sourceSchema = validation.schema.orEmpty(),
                version = validation.version ?: 1,
                documentType = documentType,
                warnings = listOf("${documentType.wireType} is validated but has no writable import records in P4-002.")
            )
        }

        return ProtocolImportReadResult(
            document = document,
            validation = validation,
            warnings = validation.warnings + document.warnings
        )
    }

    private fun readFullBackup(
        root: JsonObject,
        validation: ProtocolValidationResult,
        documentType: ProtocolDocumentType
    ): ProtocolImportDocument {
        val sessions = root.arrayField("sessions")
            ?.values
            ?.mapNotNull { (it as? JsonObject)?.toImportSession() }
            .orEmpty()
        val profiles = root.arrayField("llm_profiles")
            ?.values
            ?.mapNotNull { (it as? JsonObject)?.toImportLlmProfile() }
            .orEmpty()
        val connections = root.arrayField("provider_connections")
            ?.values
            ?.mapNotNull { (it as? JsonObject)?.toImportProviderConnection() }
            .orEmpty()
        val bindings = root.arrayField("model_bindings")
            ?.values
            ?.mapNotNull { (it as? JsonObject)?.toImportModelBinding() }
            .orEmpty()
        val graphWarning = if (root.objectField("graph") != null) {
            listOf("Graph payload was detected; graph import is deferred to Phase 5.")
        } else {
            emptyList()
        }

        return ProtocolImportDocument(
            sourceSchema = validation.schema.orEmpty(),
            version = validation.version ?: 1,
            documentType = documentType,
            sessions = sessions,
            llmProfiles = profiles,
            providerConnections = connections,
            modelBindings = bindings,
            warnings = graphWarning
        )
    }

    private fun readSessionExport(
        root: JsonObject,
        validation: ProtocolValidationResult,
        documentType: ProtocolDocumentType
    ): ProtocolImportDocument {
        val session = root.objectField("session")?.toImportSession()
        val fallbackSessionId = session?.id
        val messages = root.arrayField("messages")
            ?.values
            ?.mapNotNull { (it as? JsonObject)?.toImportMessage(fallbackSessionId) }
            .orEmpty()

        return ProtocolImportDocument(
            sourceSchema = validation.schema.orEmpty(),
            version = validation.version ?: 1,
            documentType = documentType,
            sessions = listOfNotNull(session),
            messages = messages
        )
    }

    private fun readPreset(
        root: JsonObject,
        validation: ProtocolValidationResult,
        documentType: ProtocolDocumentType
    ): ProtocolImportDocument {
        val title = root.stringField("title").orEmpty()
        val role = root.stringField("role").orEmpty()
        val goal = root.stringField("goal").orEmpty()
        val profile = root.stringField("profile").orEmpty()
        val session = ProtocolImportSessionRecord(
            id = "preset-${title.stableProtocolId()}",
            title = title,
            systemPrompt = listOf(
                "Role: $role",
                "Goal: $goal",
                "Profile: $profile"
            ).joinToString(separator = "\n")
        )

        return ProtocolImportDocument(
            sourceSchema = validation.schema.orEmpty(),
            version = validation.version ?: 1,
            documentType = documentType,
            sessions = listOf(session)
        )
    }

    private fun readLlmProfile(
        root: JsonObject,
        validation: ProtocolValidationResult,
        documentType: ProtocolDocumentType
    ): ProtocolImportDocument =
        ProtocolImportDocument(
            sourceSchema = validation.schema.orEmpty(),
            version = validation.version ?: 1,
            documentType = documentType,
            llmProfiles = listOfNotNull(root.toImportLlmProfile())
        )

    private fun readLegacyExport(
        root: JsonObject,
        validation: ProtocolValidationResult,
        documentType: ProtocolDocumentType
    ): ProtocolImportDocument {
        val payload = root.objectField("payload")
        val warning = if (payload?.stringField("full_backup_schema") == ProtocolModule.fullBackupSchema) {
            "Legacy wrapper detected; nested full-backup payload is required before records can be written."
        } else {
            "Legacy export wrapper validated; no writable records were found."
        }
        return ProtocolImportDocument(
            sourceSchema = validation.schema.orEmpty(),
            version = validation.version ?: 1,
            documentType = documentType,
            warnings = listOf(warning)
        )
    }
}

internal fun JsonObject.objectField(name: String): JsonObject? =
    fields[name] as? JsonObject

internal fun JsonObject.arrayField(name: String): JsonArray? =
    fields[name] as? JsonArray

private fun JsonObject.toImportSession(): ProtocolImportSessionRecord? {
    val id = stringField("id")?.trim().orEmpty()
    val title = stringField("title")?.trim().orEmpty()
    if (id.isEmpty() || title.isEmpty()) return null
    return ProtocolImportSessionRecord(
        id = id,
        title = title,
        systemPrompt = stringField("system_prompt") ?: stringField("systemPrompt"),
        modelBindingId = stringField("model_binding_id") ?: stringField("llm_profile_id")
    )
}

private fun JsonObject.toImportMessage(fallbackSessionId: String?): ProtocolImportMessageRecord? {
    val id = stringField("id")?.trim().orEmpty()
    val role = stringField("role")?.trim().orEmpty()
    val text = stringField("text")?.trim().orEmpty()
    if (id.isEmpty() || role.isEmpty() || text.isEmpty()) return null
    return ProtocolImportMessageRecord(
        id = id,
        sessionId = stringField("session_id") ?: stringField("sessionId") ?: fallbackSessionId,
        role = role,
        text = text
    )
}

private fun JsonObject.toImportLlmProfile(): ProtocolImportLlmProfileRecord? {
    val id = stringField("id")?.trim().orEmpty()
    val name = stringField("name")?.trim().orEmpty()
    val provider = stringField("provider") ?: stringField("api_type")
    val model = stringField("model")?.trim().orEmpty()
    if (id.isEmpty() || name.isEmpty() || provider.isNullOrBlank() || model.isEmpty()) return null
    return ProtocolImportLlmProfileRecord(
        id = id,
        name = name,
        provider = provider,
        model = model,
        baseUrl = stringField("base_url") ?: stringField("baseUrl")
    )
}

private fun JsonObject.toImportProviderConnection(): ProtocolImportProviderConnectionRecord? {
    val id = stringField("id")?.trim().orEmpty()
    val name = stringField("name")?.trim().orEmpty()
    val protocol = stringField("protocol")?.trim().orEmpty()
    if (id.isEmpty() || name.isEmpty() || protocol.isEmpty()) return null
    return ProtocolImportProviderConnectionRecord(
        id = id,
        name = name,
        protocol = protocol,
        providerName = stringField("provider_name"),
        baseUrl = stringField("base_url")
    )
}

private fun JsonObject.toImportModelBinding(): ProtocolImportModelBindingRecord? {
    val id = stringField("id")?.trim().orEmpty()
    val connectionId = stringField("connection_id")?.trim().orEmpty()
    val modelId = stringField("model_id")?.trim().orEmpty()
    if (id.isEmpty() || connectionId.isEmpty() || modelId.isEmpty()) return null
    return ProtocolImportModelBindingRecord(
        id = id,
        connectionId = connectionId,
        modelId = modelId,
        displayName = stringField("display_name"),
        enabled = (fields["enabled"] as? JsonBoolean)?.value ?: true
    )
}

private fun String.stableProtocolId(): String {
    val slug = trim()
        .lowercase()
        .map { char -> if (char.isLetterOrDigit()) char else '-' }
        .joinToString(separator = "")
        .replace(Regex("-+"), "-")
        .trim('-')
        .take(48)
    return slug.ifEmpty { "imported-session" }
}
