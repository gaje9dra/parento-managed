package com.parento.managed.screenshare

import android.content.Context
import android.content.Intent

interface ScreenCaptureController {
    fun start(resultCode: Int, resultData: Intent, sessionId: String): Result<Unit>
    fun stop()
}

class ScreenFrameSource {
    /**
     * Output boundary for the future approved media transport.
     *
     * This phase consumes frames only long enough to release them. It does not
     * encode, persist, transmit, or archive screen content.
     */
    fun onFrameAvailable() = Unit
}
