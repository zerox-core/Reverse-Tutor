package com.reversetutor.core.data.model

import androidx.room.withTransaction
import com.reversetutor.core.data.local.ReverseTutorDatabase
import com.reversetutor.core.data.local.entity.toDomain
import com.reversetutor.core.data.local.entity.toEntity
import com.reversetutor.core.model.ModelBinding
import com.reversetutor.core.model.ProviderConnection

class ModelConnectionRepositoryImpl(
    private val database: ReverseTutorDatabase
) {
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
        database.modelConnectionDao().getConnection(id)?.toDomain()

    suspend fun getBinding(id: String): ModelBinding? =
        database.modelConnectionDao().getBinding(id)?.toDomain()

    suspend fun listConnections(spaceId: String): List<ProviderConnection> =
        database.modelConnectionDao().listConnections(spaceId).map { it.toDomain() }

    suspend fun listBindings(spaceId: String): List<ModelBinding> =
        database.modelConnectionDao().listBindings(spaceId).map { it.toDomain() }

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
