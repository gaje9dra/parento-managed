package com.parento.managed.lifecycle

import com.parento.managed.data.LocalApplicationState
import com.parento.managed.data.LocalStateRepository
import com.parento.managed.device.CapabilityState
import com.parento.managed.device.DeviceManagementManager
import com.parento.managed.device.ManagementMode
import com.parento.managed.domain.OperationResult
import com.parento.managed.policy.PolicyEngine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ManagementState(
    val managementMode: ManagementMode,
    val capabilities: List<CapabilityState>,
    val evaluatedAtEpochMillis: Long,
)

interface ManagementStateRepository {
    suspend fun read(): OperationResult<ManagementState?>
    suspend fun write(state: ManagementState): OperationResult<Unit>
}

class LocalManagementStateRepository(
    private val localStateRepository: LocalStateRepository,
) : ManagementStateRepository {
    override suspend fun read(): OperationResult<ManagementState?> =
        when (val result = localStateRepository.read()) {
            is OperationResult.Failure -> result
            is OperationResult.Success -> result.value?.let {
                OperationResult.Success(
                    ManagementState(
                        managementMode = it.managementMode,
                        capabilities = it.managementCapabilities,
                        evaluatedAtEpochMillis = it.managementStateUpdatedAtEpochMillis ?: 0L,
                    ),
                )
            } ?: OperationResult.Success(null)
        }

    override suspend fun write(state: ManagementState): OperationResult<Unit> =
        when (val current = localStateRepository.read()) {
            is OperationResult.Failure -> current
            is OperationResult.Success -> localStateRepository.write(
                (current.value ?: LocalApplicationState()).copy(
                    managementMode = state.managementMode,
                    managementCapabilities = state.capabilities,
                    managementStateUpdatedAtEpochMillis = state.evaluatedAtEpochMillis,
                ),
            )
        }
}

class ManagedDeviceInitializer(
    private val localStateRepository: LocalStateRepository,
    private val managementStateRepository: ManagementStateRepository,
    private val deviceManagementManager: DeviceManagementManager,
    @Suppress("UNUSED_PARAMETER") private val policyEngine: PolicyEngine,
) {
    private val mutex = Mutex()

    suspend fun initialize(): OperationResult<ManagementState> =
        mutex.withLock {
            when (val local = localStateRepository.initializeLocalState()) {
                is OperationResult.Failure -> local
                is OperationResult.Success -> {
                    val state = ManagementState(
                        managementMode = deviceManagementManager.detectManagementMode(),
                        capabilities = deviceManagementManager.evaluateCapabilities(),
                        evaluatedAtEpochMillis = System.currentTimeMillis(),
                    )
                    when (val persisted = managementStateRepository.write(state)) {
                        is OperationResult.Failure -> persisted
                        is OperationResult.Success -> OperationResult.Success(state)
                    }
                }
            }
        }
}
