package com.reversetutor.core.data.model

import androidx.room.withTransaction
import com.reversetutor.core.data.local.ReverseTutorDatabase
import com.reversetutor.core.data.local.entity.toDomain
import com.reversetutor.core.data.local.entity.toEntity
import com.reversetutor.core.domain.ModelConnectionRepository
import com.reversetutor.core.model.ModelBinding
import com.reversetutor.core.model.ProviderConnection

interface ExecutionModelResolver {
    suspend fun resolveForExecution(
        sessionId: String,
        requestedBindingId: String? = null
    ): ExecutionModelConfiguration?

    suspend fun hasNewConfigurationForSession(sessionId: String): Boolean
}

data class ExecutionModelConfiguration(
    val connection: ProviderConnection,
    val binding: ModelBinding
)

class ModelConnectionRepositoryImpl(
    private val database: ReverseTutorDatabase
) : ModelConnectionRepository, ExecutionModelResolver {
    override suspend fun findConnection(connectionId: String): ProviderConnection? =
        database.modelConnectionDao().getConnection(connectionId)?.toDomain()

    override suspend fun findBinding(bindingId: String): ModelBinding? =
        database.modelConnectionDao().getBinding(bindingId)?.toDomain()

    override suspend fun listBindings(connectionId: String): List<ModelBinding> =
        database.modelConnectionDao().listBindingsByConnection(connectionId).map { it.toDomain() }

    override suspend fun saveBinding(binding: ModelBinding): ModelBinding {
        database.modelConnectionDao().upsertBinding(binding.toEntity())
        return binding
    }

    suspend fun saveConnection(connection: ProviderConnection) {
        database.modelConnectionDao().upsertConnection(connection.toEntity())
    }

    suspend fun saveConnectionWithBindings(
        connection: ProviderConnection,
        bindings: List<ModelBinding>
    ) {
        require(bindings.all { it.connectionId == connection.id && it.spaceId == connection.spaceId })
        database.withTransaction {
            database.modelConnectionDao().upsertConnection(connection.toEntity())
            bindings.forEach { database.modelConnectionDao().upsertBinding(it.toEntity()) }
        }
    }

    suspend fun getConnection(id: String): ProviderConnection? =
        findConnection(id)

    suspend fun getBinding(id: String): ModelBinding? =
        findBinding(id)

    suspend fun listConnections(spaceId: String): List<ProviderConnection> =
        database.modelConnectionDao().listConnections(spaceId).map { it.toDomain() }

    suspend fun listBindingsBySpace(spaceId: String): List<ModelBinding> =
        database.modelConnectionDao().listBindings(spaceId).map { it.toDomain() }

    override suspend fun resolveForExecution(
        sessionId: String,
        requestedBindingId: String?
    ): ExecutionModelConfiguration? {
        val session = database.sessionDao().getById(sessionId) ?: return null
        val selectedBindingId = requestedBindingId.normalizedId()
            ?: session.modelBindingId.normalizedId()
        val binding = if (selectedBindingId != null) {
            database.modelConnectionDao().getBinding(selectedBindingId)?.toDomain()
        } else {
            database.modelConnectionDao().listBindings(session.spaceId)
                .asSequence()
                .map { it.toDomain() }
                .firstOrNull { it.isDefault && it.enabled }
        } ?: return null
        if (!binding.enabled || binding.spaceId != session.spaceId) return null

        val connection = database.modelConnectionDao().getConnection(binding.connectionId)
            ?.toDomain()
            ?: return null
        if (!connection.enabled || connection.spaceId != session.spaceId) return null
        return ExecutionModelConfiguration(connection, binding)
    }

    override suspend fun hasNewConfigurationForSession(sessionId: String): Boolean {
        val session = database.sessionDao().getById(sessionId) ?: return false
        if (session.modelBindingId.normalizedId() != null) return true
        return database.modelConnectionDao().listBindings(session.spaceId).isNotEmpty() ||
            database.modelConnectionDao().listConnections(session.spaceId).isNotEmpty()
    }

    suspend fun deleteConnectionIfUnused(id: String): Boolean =
        database.withTransaction {
            if (
                database.modelConnectionDao().countSessionBindings(id) > 0 ||
                database.modelConnectionDao().countRunBindings(id) > 0
            ) {
                false
            } else {
                database.modelConnectionDao().deleteConnection(id) > 0
            }
        }
}

private fun String?.normalizedId(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
