package com.reversetutor.core.data.companion

import com.reversetutor.core.data.local.dao.CompanionMemoryDao
import com.reversetutor.core.data.local.entity.CompanionMemoryVersionEntity
import com.reversetutor.core.data.local.entity.MemoryObservationEntity
import com.reversetutor.core.data.session.SessionRepository
import com.reversetutor.core.domain.ActiveMemoryVersion
import com.reversetutor.core.domain.CompanionMemoryEvolutionPolicy
import com.reversetutor.core.domain.CompanionMemoryPartition
import com.reversetutor.core.domain.MemoryDomain
import com.reversetutor.core.domain.MemoryObservation
import com.reversetutor.core.domain.MemoryOrigin
import com.reversetutor.core.domain.WindowRef
import java.util.UUID

/**
 * Companion-memory repository. Exposes only domain-safe `core:domain` contracts.
 *
 * Companion memory is exclusive to the companion root: task and child windows
 * are denied read and write (see [CompanionMemoryEvolutionPolicy.canReadDomain]
 * and [CompanionMemoryEvolutionPolicy.canEmit]). Observations are
 * provenance-only; no raw conversation text is ever persisted.
 */
class CompanionMemoryRepository(
    private val dao: CompanionMemoryDao,
    private val defaultSpaceId: String = SessionRepository.defaultSpaceId
) {

    suspend fun readVersions(window: WindowRef): List<ActiveMemoryVersion>? {
        if (!CompanionMemoryEvolutionPolicy.canReadDomain(MemoryDomain.COMPANION, window)) return null
        return dao.listVersions(window.id).map { it.toDomain() }
    }

    suspend fun saveVersion(
        window: WindowRef,
        version: ActiveMemoryVersion,
        spaceId: String = defaultSpaceId
    ): ActiveMemoryVersion? {
        if (!CompanionMemoryEvolutionPolicy.canReadDomain(MemoryDomain.COMPANION, window)) return null
        dao.upsertVersion(version.toEntity(window.id, spaceId))
        return version
    }

    suspend fun appendObservation(
        window: WindowRef,
        observation: MemoryObservation,
        spaceId: String = defaultSpaceId
    ): Boolean {
        if (!CompanionMemoryEvolutionPolicy.canEmit(observation, window)) return false
        dao.insertObservation(observation.toEntity(window.id, spaceId))
        return true
    }

    suspend fun readObservations(window: WindowRef): List<MemoryObservation>? {
        if (!CompanionMemoryEvolutionPolicy.canReadDomain(MemoryDomain.COMPANION, window)) return null
        return dao.listObservations(window.id).map { it.toDomain() }
    }
}

private fun ActiveMemoryVersion.toEntity(windowId: String, spaceId: String) = CompanionMemoryVersionEntity(
    id = "$windowId::${partition.name}",
    windowId = windowId,
    spaceId = spaceId,
    partition = partition.name,
    value = value,
    origin = origin.name,
    promotedAtEpochMillis = promotedAtEpochMillis,
    revision = revision
)

private fun CompanionMemoryVersionEntity.toDomain() = ActiveMemoryVersion(
    partition = CompanionMemoryPartition.valueOf(partition),
    value = value,
    origin = MemoryOrigin.valueOf(origin),
    promotedAtEpochMillis = promotedAtEpochMillis,
    revision = revision
)

private fun MemoryObservation.toEntity(windowId: String, spaceId: String) = MemoryObservationEntity(
    id = "obs-${UUID.randomUUID()}",
    windowId = windowId,
    spaceId = spaceId,
    domain = domain.name,
    partition = partition?.name,
    normalizedValue = normalizedValue,
    sourceClass = sourceClass,
    observedAtEpochMillis = observedAtEpochMillis,
    confidence = confidence,
    provenanceHandle = provenanceHandle
)

private fun MemoryObservationEntity.toDomain() = MemoryObservation(
    domain = MemoryDomain.valueOf(domain),
    partition = partition?.let { CompanionMemoryPartition.valueOf(it) },
    normalizedValue = normalizedValue,
    sourceClass = sourceClass,
    observedAtEpochMillis = observedAtEpochMillis,
    confidence = confidence,
    provenanceHandle = provenanceHandle
)
