package com.parento.managed.domain

data class ManagedDevice(
    val deviceId: String,
    val status: DeviceStatus,
    val enrollmentState: EnrollmentState,
    val policyState: PolicyState,
    val connectionState: ConnectionState,
)
