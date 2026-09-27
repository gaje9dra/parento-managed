package com.parento.managed.network

import com.parento.managed.data.local.NetworkPolicyStateDao
import com.parento.managed.data.local.NetworkPolicyStateEntity
import com.parento.managed.domain.ManagedError
import com.parento.managed.domain.OperationResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface NetworkPolicyStateRepository {
    suspend fun read(): OperationResult<LocalNetworkPolicyState>
    suspend fun write(state: LocalNetworkPolicyState): OperationResult<Unit>
}

class RoomNetworkPolicyStateRepository(
    private val dao: NetworkPolicyStateDao,
) : NetworkPolicyStateRepository {
    private val mutex = Mutex()

    override suspend fun read(): OperationResult<LocalNetworkPolicyState> = runCatching {
        dao.read()?.toDomain() ?: LocalNetworkPolicyState()
    }.fold(
        onSuccess = { OperationResult.Success(it) },
        onFailure = { OperationResult.Failure(ManagedError.STORAGE_FAILURE) },
    )

    override suspend fun write(state: LocalNetworkPolicyState): OperationResult<Unit> =
        mutex.withLock {
            runCatching { dao.upsert(state.toEntity()) }.fold(
                onSuccess = { OperationResult.Success(Unit) },
                onFailure = { OperationResult.Failure(ManagedError.STORAGE_FAILURE) },
            )
        }

    private fun NetworkPolicyStateEntity.toDomain(): LocalNetworkPolicyState =
        LocalNetworkPolicyState(
            desiredPolicyId = desiredPolicyId,
            desiredPolicyVersion = desiredPolicyVersion,
            desiredPolicyJson = desiredPolicyJson,
            appliedPolicyId = appliedPolicyId,
            appliedPolicyVersion = appliedPolicyVersion,
            enforcementStatus = runCatching { NetworkEnforcementStatus.valueOf(enforcementStatus) }
                .getOrElse { throw IllegalStateException("Invalid network enforcement status.") },
            capabilityMode = runCatching { NetworkCapabilityMode.valueOf(capabilityMode) }
                .getOrElse { throw IllegalStateException("Invalid network capability mode.") },
            capabilitySupported = capabilitySupported,
            capabilityVersion = capabilityVersion,
            lastSynchronizedAtEpochMillis = lastSynchronizedAtEpochMillis,
            pendingSynchronization = pendingSynchronization,
            lastErrorCode = lastErrorCode,
        )

    private fun LocalNetworkPolicyState.toEntity(): NetworkPolicyStateEntity =
        NetworkPolicyStateEntity(
            desiredPolicyId = desiredPolicyId,
            desiredPolicyVersion = desiredPolicyVersion,
            desiredPolicyJson = desiredPolicyJson,
            appliedPolicyId = appliedPolicyId,
            appliedPolicyVersion = appliedPolicyVersion,
            enforcementStatus = enforcementStatus.name,
            capabilityMode = capabilityMode.name,
            capabilitySupported = capabilitySupported,
            capabilityVersion = capabilityVersion,
            lastSynchronizedAtEpochMillis = lastSynchronizedAtEpochMillis,
            pendingSynchronization = pendingSynchronization,
            lastErrorCode = lastErrorCode,
        )
}
