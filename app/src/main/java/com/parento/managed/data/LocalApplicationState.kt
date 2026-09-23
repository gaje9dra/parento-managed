package com.parento.managed.data

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
)
