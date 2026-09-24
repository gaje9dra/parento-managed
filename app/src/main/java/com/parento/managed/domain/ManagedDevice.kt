package com.parento.managed.domain

/**
 * Domain representation of an enrolled Managed device.
 *
 * The identifier is backend-assigned; local installation identity is separate.
 */
data class ManagedDevice(
    val managedDeviceId: String,
    val status: DeviceStatus,
    val enrollmentState: EnrollmentState,
    val policyState: PolicyState,
    val connectionState: ConnectionState,
)
