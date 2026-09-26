package com.parento.managed

import com.parento.managed.audio.parseAudioSessionId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

class AudioAccessCommandPayloadTest {
    private val sessionId = UUID.randomUUID().toString()

    @Test
    fun acceptsExactAudioSessionPayload() {
        assertEquals(sessionId, parseAudioSessionId("""{"audioSessionId":"$sessionId"}"""))
    }

    @Test
    fun rejectsLegacyAndAdditionalFields() {
        assertNull(parseAudioSessionId("""{"sessionId":"$sessionId"}"""))
        assertNull(parseAudioSessionId("""{"audioSessionId":"$sessionId","extra":"unexpected"}"""))
    }

    @Test
    fun rejectsInvalidOrEmptyPayloads() {
        assertNull(parseAudioSessionId("""{"audioSessionId":"not-a-uuid"}"""))
        assertNull(parseAudioSessionId("{}"))
        assertNull(parseAudioSessionId("""{"audioSessionId":""}"""))
    }
}
