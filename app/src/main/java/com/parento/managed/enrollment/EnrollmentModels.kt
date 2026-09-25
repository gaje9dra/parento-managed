package com.parento.managed.enrollment

data class EnrollmentAuthorization(
    val enrollmentId: String,
    val authorizationSecret: String,
)

data class EnrollmentResult(
    val managedDeviceId: String,
    val enrollmentId: String,
    val expiresAtEpochMillis: Long,
)

data class PendingEnrollment(
    val enrollmentId: String,
    val authorizationSecret: String,
    val expiresAtEpochMillis: Long,
)
