package com.parento.managed

import com.parento.managed.screenshare.ScreenCaptureState
import com.parento.managed.screenshare.ScreenCaptureStateMachine
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenCaptureStateMachineTest {
    @Test
    fun active_canStop_butCannotRestartDirectly() {
        assertTrue(ScreenCaptureStateMachine.canTransition(ScreenCaptureState.ACTIVE, ScreenCaptureState.STOPPING))
        assertFalse(ScreenCaptureStateMachine.canTransition(ScreenCaptureState.ACTIVE, ScreenCaptureState.STARTING))
    }

    @Test
    fun authorizationRequired_canBecomeAuthorizedOrFail() {
        assertTrue(ScreenCaptureStateMachine.canTransition(ScreenCaptureState.AUTHORIZATION_REQUIRED, ScreenCaptureState.AUTHORIZED))
        assertTrue(ScreenCaptureStateMachine.canTransition(ScreenCaptureState.AUTHORIZATION_REQUIRED, ScreenCaptureState.FAILED))
    }

    @Test
    fun terminalState_requiresNewRequest() {
        assertTrue(ScreenCaptureStateMachine.canTransition(ScreenCaptureState.REVOKED, ScreenCaptureState.REQUESTED))
        assertFalse(ScreenCaptureStateMachine.canTransition(ScreenCaptureState.REVOKED, ScreenCaptureState.ACTIVE))
    }
}
