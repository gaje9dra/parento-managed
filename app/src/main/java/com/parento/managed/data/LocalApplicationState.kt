package com.parento.managed.data

import com.parento.managed.device.CapabilityState
import com.parento.managed.device.CapabilityStatus
import com.parento.managed.device.DeviceManagementCapability
import com.parento.managed.device.ManagementMode
import com.parento.managed.domain.ConnectionState
import com.parento.managed.domain.EnrollmentState
import com.parento.managed.application.ApplicationEnforcementStatus
import com.parento.managed.application.ApplicationInventorySyncStatus
import com.parento.managed.application.ApplicationPolicySyncStatus

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
    val managedDeviceId: String? = null,
    val enrollmentState: EnrollmentState = EnrollmentState.UNENROLLED,
    val connectionState: ConnectionState = ConnectionState.UNKNOWN,
    val managementMode: ManagementMode = ManagementMode.NOT_MANAGED,
    val managementCapabilities: List<CapabilityState> = emptyList(),
    val managementStateUpdatedAtEpochMillis: Long? = null,
    val applicationInventorySyncStatus: ApplicationInventorySyncStatus = ApplicationInventorySyncStatus.NEVER_SYNCED,
    val lastApplicationInventoryObservedAtEpochMillis: Long? = null,
    val lastApplicationInventorySuccessfulSyncAtEpochMillis: Long? = null,
    val desiredApplicationPolicyId: String? = null,
    val desiredApplicationPolicyVersion: Int? = null,
    val acceptedApplicationPolicyVersion: Int? = null,
    val applicationPolicySyncStatus: ApplicationPolicySyncStatus = ApplicationPolicySyncStatus.NONE,
    val applicationEnforcementStatus: ApplicationEnforcementStatus = ApplicationEnforcementStatus.UNKNOWN,
)
