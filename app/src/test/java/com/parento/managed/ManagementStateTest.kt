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
            override fun evaluateCapabilities() = listOf(
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
    }
}
