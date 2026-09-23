package com.parento.managed

import com.parento.managed.domain.ConnectionState
import com.parento.managed.domain.DeviceStatus
import com.parento.managed.domain.EnrollmentState
import com.parento.managed.domain.ManagedDevice
import com.parento.managed.domain.OperationResult
import com.parento.managed.domain.PolicyState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchitectureTest {
    @Test
    fun managedDevice_canRepresentLifecycleState() {
        val device = ManagedDevice(
            deviceId = "device-1",
            status = DeviceStatus.ENROLLED,
            enrollmentState = EnrollmentState.ENROLLED,
            policyState = PolicyState.NOT_SYNCED,
            connectionState = ConnectionState.DISCONNECTED,
        )

        assertEquals(DeviceStatus.ENROLLED, device.status)
        assertEquals(EnrollmentState.ENROLLED, device.enrollmentState)
        assertEquals(ConnectionState.DISCONNECTED, device.connectionState)
    }

    @Test
    fun operationResult_representsFailureWithoutThrowing() {
        val result: OperationResult<Unit> =
            OperationResult.Failure(com.parento.managed.domain.ManagedError.NETWORK_FAILURE)

        assertTrue(result is OperationResult.Failure)
        assertEquals(
            com.parento.managed.domain.ManagedError.NETWORK_FAILURE,
            (result as OperationResult.Failure).error,
        )
    }
}
