package com.parento.managed.data

import com.parento.managed.device.CapabilityState
import com.parento.managed.device.CapabilityStatus
import com.parento.managed.device.DeviceManagementCapability
import com.parento.managed.device.ManagementMode
import com.parento.managed.domain.ConnectionState
import com.parento.managed.domain.EnrollmentState

data class LocalDeviceIdentity(
    val installationId: String,
    val createdAtEpochMillis: Long,
)

data class LocalApplicationState(
    val stateVersion: Int = 1,
    val lastSynchronizationTimestamp: Long? = null,
    val initialized: Boolean = false,
    val installationId: String? = null,
    val identityCreatedAtEpochMillis: Long? = null,
    val enrollmentState: EnrollmentState = EnrollmentState.UNENROLLED,
    val connectionState: ConnectionState = ConnectionState.UNKNOWN,
    val managementMode: ManagementMode = ManagementMode.NOT_MANAGED,
    val managementCapabilities: List<CapabilityState> = emptyList(),
    val managementStateUpdatedAtEpochMillis: Long? = null,
)
