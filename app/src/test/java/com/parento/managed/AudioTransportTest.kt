package com.parento.managed

import com.parento.managed.audio.UnavailableAudioTransport
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioTransportTest {
    @Test
    fun unavailableTransportFailsClosedWithoutAcceptingAudio() {
        val transport = UnavailableAudioTransport()
        assertFalse(transport.isAvailable())
        assertTrue(transport.start("00000000-0000-4000-8000-000000000000").isFailure)
        assertTrue(transport.send(ByteArray(32), 32, 1L).isFailure)
    }
}
