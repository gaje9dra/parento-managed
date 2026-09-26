package com.parento.managed

import com.parento.managed.screenshare.parseScreenSessionId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

class ScreenShareCommandPayloadTest {
    private val sessionId = UUID.randomUUID().toString()

    @Test
    fun acceptsBackendScreenSessionIdOnly() {
        assertEquals(
            sessionId,
            parseScreenSessionId("""{"screenSessionId":"$sessionId"}"""),
        )
    }

    @Test
    fun rejectsLegacySessionIdField() {
        assertNull(parseScreenSessionId("""{"sessionId":"$sessionId"}"""))
    }

    @Test
    fun rejectsAdditionalPayloadFields() {
        assertNull(
            parseScreenSessionId(
                """{"screenSessionId":"$sessionId","extra":"unexpected"}""",
            ),
        )
    }

    @Test
    fun rejectsMalformedOrInvalidSessionId() {
        assertNull(parseScreenSessionId("""{"screenSessionId":"not-a-uuid"}"""))
        assertNull(parseScreenSessionId("""{"screenSessionId":""}"""))
        assertNull(parseScreenSessionId("{}"))
    }
}
