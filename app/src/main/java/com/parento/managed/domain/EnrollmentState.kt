package com.parento.managed.domain

enum class EnrollmentState {
    UNENROLLED,
    ENROLLING,
    ENROLLED,
    REVOKED,
    ERROR,
}

fun EnrollmentState.canTransitionTo(target: EnrollmentState): Boolean =
    when (this) {
        EnrollmentState.UNENROLLED ->
            target == EnrollmentState.ENROLLING || target == EnrollmentState.ERROR
        EnrollmentState.ENROLLING ->
            target == EnrollmentState.UNENROLLED ||
                target == EnrollmentState.ENROLLED ||
                target == EnrollmentState.ERROR
        EnrollmentState.ENROLLED ->
            target == EnrollmentState.REVOKED || target == EnrollmentState.ERROR
        EnrollmentState.REVOKED -> false
        EnrollmentState.ERROR ->
            target == EnrollmentState.UNENROLLED || target == EnrollmentState.ENROLLING
    }
