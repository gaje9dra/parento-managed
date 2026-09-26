package com.parento.managed.screenshare

object ScreenCaptureStateMachine {
    fun canTransition(from: ScreenCaptureState, to: ScreenCaptureState): Boolean {
        if (from == to) return true
        return when (from) {
            ScreenCaptureState.UNAVAILABLE -> to == ScreenCaptureState.AUTHORIZATION_REQUIRED ||
                to == ScreenCaptureState.REQUESTED ||
                to == ScreenCaptureState.STOPPED
            ScreenCaptureState.REQUESTED -> to in setOf(
                ScreenCaptureState.AUTHORIZATION_REQUIRED,
                ScreenCaptureState.FAILED,
                ScreenCaptureState.EXPIRED,
                ScreenCaptureState.REVOKED,
            )
            ScreenCaptureState.AUTHORIZATION_REQUIRED -> to in setOf(
                ScreenCaptureState.AUTHORIZED,
                ScreenCaptureState.FAILED,
                ScreenCaptureState.EXPIRED,
                ScreenCaptureState.REVOKED,
            )
            ScreenCaptureState.AUTHORIZED -> to in setOf(
                ScreenCaptureState.STARTING,
                ScreenCaptureState.FAILED,
                ScreenCaptureState.EXPIRED,
                ScreenCaptureState.REVOKED,
            )
            ScreenCaptureState.STARTING -> to in setOf(
                ScreenCaptureState.ACTIVE,
                ScreenCaptureState.STOPPING,
                ScreenCaptureState.FAILED,
                ScreenCaptureState.EXPIRED,
                ScreenCaptureState.REVOKED,
            )
            ScreenCaptureState.ACTIVE -> to in setOf(
                ScreenCaptureState.STOPPING,
                ScreenCaptureState.STOPPED,
                ScreenCaptureState.FAILED,
                ScreenCaptureState.EXPIRED,
                ScreenCaptureState.REVOKED,
            )
            ScreenCaptureState.STOPPING -> to in setOf(
                ScreenCaptureState.STOPPED,
                ScreenCaptureState.FAILED,
                ScreenCaptureState.REVOKED,
                ScreenCaptureState.EXPIRED,
            )
            ScreenCaptureState.STOPPED,
            ScreenCaptureState.FAILED,
            ScreenCaptureState.REVOKED,
            ScreenCaptureState.EXPIRED,
            -> to == ScreenCaptureState.REQUESTED ||
                to == ScreenCaptureState.AUTHORIZATION_REQUIRED ||
                to == ScreenCaptureState.UNAVAILABLE
        }
    }
}
