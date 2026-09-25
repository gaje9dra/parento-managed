package com.parento.managed

import com.parento.managed.data.LocalApplicationState
import com.parento.managed.data.LocalDeviceIdentity
import com.parento.managed.data.LocalStateRepository
import com.parento.managed.device.CapabilityState
import com.parento.managed.device.CapabilityStatus
import com.parento.managed.device.DeviceManagementCapability
import com.parento.managed.device.DeviceManagementManager
import com.parento.managed.device.ManagementMode
import com.parento.managed.domain.EnrollmentState
import com.parento.managed.domain.ManagedError
import com.parento.managed.domain.OperationResult
import com.parento.managed.lifecycle.LocalManagementStateRepository
import com.parento.managed.lifecycle.ManagedDeviceInitializer
import com.parento.managed.lifecycle.ManagementState
import com.parento.managed.policy.DefaultPolicyEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ManagementStateTest {
    @Test
    fun initializationIsIdempotentAndPersistsDetectedMode() = kotlinx.coroutines.runBlocking {
        val local = FakeLocalStateRepository()
        val manager = object : DeviceManagementManager {
            override fun detectManagementMode() = ManagementMode.PROFILE_OWNER
            override fun detectManagementState() =
                com.parento.managed.device.ManagementDetectionResult(ManagementMode.PROFILE_OWNER)
            override fun evaluateCapabilities(
                detection: com.parento.managed.device.ManagementDetectionResult,
            ) = listOf(
                CapabilityState(DeviceManagementCapability.PROFILE_OWNER, CapabilityStatus.AVAILABLE),
            )
        }
        val initializer = ManagedDeviceInitializer(
            localStateRepository = local,
            managementStateRepository = LocalManagementStateRepository(local),
            deviceManagementManager = manager,
            policyEngine = DefaultPolicyEngine(),
        )

        val first = initializer.initialize()
        val second = initializer.initialize()

        assertEquals(ManagementMode.PROFILE_OWNER, (first as OperationResult.Success).value.managementMode)
        assertEquals(ManagementMode.PROFILE_OWNER, (second as OperationResult.Success).value.managementMode)
        assertNotNull(local.read().let { (it as OperationResult.Success).value?.managementMode })
    }

    @Test
    fun initializationRefreshesCachedModeFromPlatform() = kotlinx.coroutines.runBlocking {
        val local = FakeLocalStateRepository()
        local.write(
            LocalApplicationState(
                managementMode = ManagementMode.DEVICE_OWNER,
                managementCapabilities = listOf(
                    CapabilityState(
                        DeviceManagementCapability.DEVICE_OWNER,
                        CapabilityStatus.AVAILABLE,
                    ),
                ),
            ),
        )
        val manager = object : DeviceManagementManager {
            override fun detectManagementMode() = ManagementMode.NOT_MANAGED
            override fun detectManagementState() =
                com.parento.managed.device.ManagementDetectionResult(ManagementMode.NOT_MANAGED)
            override fun evaluateCapabilities(
                detection: com.parento.managed.device.ManagementDetectionResult,
            ) = listOf(
                CapabilityState(
                    DeviceManagementCapability.DEVICE_OWNER,
                    CapabilityStatus.UNAVAILABLE,
                ),
            )
        }
        val initializer = ManagedDeviceInitializer(
            localStateRepository = local,
            managementStateRepository = LocalManagementStateRepository(local),
            deviceManagementManager = manager,
            policyEngine = DefaultPolicyEngine(),
        )

        val result = initializer.initialize() as OperationResult.Success

        assertEquals(ManagementMode.NOT_MANAGED, result.value.managementMode)
        assertEquals(
            ManagementMode.NOT_MANAGED,
            (local.read() as OperationResult.Success).value?.managementMode,
        )
    }

    private class FakeLocalStateRepository : LocalStateRepository {
        private var state: LocalApplicationState? = null

        override suspend fun read(): OperationResult<LocalApplicationState?> = OperationResult.Success(state)
        override suspend fun write(value: LocalApplicationState): OperationResult<Unit> {
            state = value
            return OperationResult.Success(Unit)
        }
        override suspend fun clear(): OperationResult<Unit> {
            state = null
            return OperationResult.Success(Unit)
        }
        override fun observe(): Flow<OperationResult<LocalApplicationState?>> = emptyFlow()
        override suspend fun getOrCreateIdentity(): OperationResult<LocalDeviceIdentity> =
            OperationResult.Success(LocalDeviceIdentity("00000000-0000-0000-0000-000000000001", 1L))
        override suspend fun getEnrollmentState(): OperationResult<EnrollmentState> =
            OperationResult.Success(state?.enrollmentState ?: EnrollmentState.UNENROLLED)
        override suspend fun getConnectionState(): OperationResult<com.parento.managed.domain.ConnectionState> =
            OperationResult.Success(state?.connectionState ?: com.parento.managed.domain.ConnectionState.UNKNOWN)
        override suspend fun getManagedDeviceId(): OperationResult<String?> =
            OperationResult.Success(state?.managedDeviceId)
        override suspend fun completeEnrollment(managedDeviceId: String): OperationResult<Unit> {
            state = (state ?: LocalApplicationState()).copy(
                managedDeviceId = managedDeviceId,
                enrollmentState = EnrollmentState.ENROLLED,
            )
            return OperationResult.Success(Unit)
        }
        override suspend fun initializeLocalState(): OperationResult<LocalApplicationState> {
            state = (state ?: LocalApplicationState()).copy(
                initialized = true,
                installationId = "00000000-0000-0000-0000-000000000001",
                identityCreatedAtEpochMillis = 1L,
            )
            return OperationResult.Success(state!!)
        }
        override suspend fun updateEnrollmentState(state: EnrollmentState): OperationResult<Unit> {
            this.state = (this.state ?: LocalApplicationState()).copy(enrollmentState = state)
            return OperationResult.Success(Unit)
        }
        override suspend fun updateConnectionState(state: com.parento.managed.domain.ConnectionState): OperationResult<Unit> {
            this.state = (this.state ?: LocalApplicationState()).copy(connectionState = state)
            return OperationResult.Success(Unit)
        }
    }
}
