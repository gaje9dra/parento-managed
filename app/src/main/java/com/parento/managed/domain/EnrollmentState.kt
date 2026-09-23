package com.parento.managed.domain

enum class EnrollmentState {
    UNENROLLED,
    ENROLLING,
    ENROLLED,
    CONNECTED,
    DISCONNECTED,
    REVOKED,
    ERROR,
}

fun EnrollmentState.canTransitionTo(target: EnrollmentState): Boolean =
    when (this) {
        UNENROLLED -> target == ENROLLING || target == ERROR
        ENROLLING -> target == UNENROLLED || target == ENROLLED || target == ERROR
        ENROLLED -> target == CONNECTED || target == DISCONNECTED ||
            target == REVOKED || target == ERROR
        CONNECTED -> target == DISCONNECTED || target == REVOKED || target == ERROR
        DISCONNECTED -> target == CONNECTED || target == REVOKED || target == ERROR
        REVOKED -> false
        ERROR -> target == UNENROLLED || target == ENROLLING
    }
