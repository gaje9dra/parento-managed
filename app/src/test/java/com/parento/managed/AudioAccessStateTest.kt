package com.parento.managed

import com.parento.managed.audio.AudioAccessState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AudioAccessStateTest {
    @Test
    fun startsIdleAndHasExplicitPermissionState() {
        assertEquals(AudioAccessState.IDLE, AudioAccessState.entries.first())
        assertNotEquals(AudioAccessState.IDLE, AudioAccessState.PERMISSION_REQUIRED)
    }

    @Test
    fun terminalStatesAreDistinctFromActiveState() {
        assertNotEquals(AudioAccessState.ACTIVE, AudioAccessState.STOPPED)
        assertNotEquals(AudioAccessState.ACTIVE, AudioAccessState.FAILED)
    }
}
