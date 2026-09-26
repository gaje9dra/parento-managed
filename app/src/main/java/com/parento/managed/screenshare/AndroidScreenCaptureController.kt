package com.parento.managed.screenshare

import android.content.ComponentCallbacks
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager

class AndroidScreenCaptureController(
    private val context: Context,
    private val onState: (ScreenCaptureState, String?) -> Unit,
) : ScreenCaptureController {

    private val lifecycleLock = Any()
    private val frameSource = ScreenFrameSource()
    private var projection: MediaProjection? = null
    private var virtualDisplay: android.hardware.display.VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var sessionId: String? = null
    private var started = false
    private var projectionCallback: MediaProjection.Callback? = null
    private var componentCallbacks: ComponentCallbacks? = null

    override fun start(resultCode: Int, resultData: Intent, sessionId: String): Result<Unit> {
        synchronized(lifecycleLock) {
            if (started || projection != null || virtualDisplay != null) {
                return Result.failure(IllegalStateException("Screen capture is already active."))
            }
            this.sessionId = sessionId
            onState(ScreenCaptureState.STARTING, null)

            return runCatching {
                val manager = context.getSystemService(MediaProjectionManager::class.java)
                    ?: error("MediaProjection is unavailable.")
                val mediaProjection = manager.getMediaProjection(resultCode, resultData)
                    ?: error("MediaProjection authorization is unavailable.")
                projection = mediaProjection

                val metrics = displayMetrics()
                val width = metrics.widthPixels.coerceAtLeast(320)
                val height = metrics.heightPixels.coerceAtLeast(320)
                val density = metrics.densityDpi.coerceAtLeast(DisplayMetrics.DENSITY_DEFAULT)

                val reader = ImageReader.newInstance(
                    width,
                    height,
                    android.graphics.PixelFormat.RGBA_8888,
                    2,
                )
                imageReader = reader
                reader.setOnImageAvailableListener(
                    { source ->
                        source.acquireLatestImage()?.use {
                            frameSource.onFrameAvailable()
                        }
                    },
                    null,
                )

                val callback = object : MediaProjection.Callback() {
                    override fun onStop() {
                        synchronized(lifecycleLock) {
                            releaseResourcesLocked()
                        }
                        onState(ScreenCaptureState.STOPPED, null)
                    }

                    override fun onCapturedContentResize(width: Int, height: Int) {
                        synchronized(lifecycleLock) {
                            virtualDisplay?.resize(
                                width.coerceAtLeast(1),
                                height.coerceAtLeast(1),
                                density,
                            )
                        }
                    }
                }
                projectionCallback = callback
                mediaProjection.registerCallback(callback, null)

                val configurationCallbacks = object : ComponentCallbacks {
                    override fun onConfigurationChanged(newConfig: Configuration) {
                        resizeToCurrentDisplay()
                    }

                    override fun onLowMemory() = Unit
                }
                componentCallbacks = configurationCallbacks
                context.registerComponentCallbacks(configurationCallbacks)

                val displayManager = context.getSystemService(DisplayManager::class.java)
                    ?: error("DisplayManager is unavailable.")
                val display = displayManager.createVirtualDisplay(
                    "Parento Screen Share",
                    width,
                    height,
                    density,
                    reader.surface,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    null,
                    null,
                ) ?: error("VirtualDisplay creation failed.")

                if (projection == null) {
                    display.release()
                    error("MediaProjection terminated during capture initialization.")
                }

                virtualDisplay = display
                started = true
                onState(ScreenCaptureState.ACTIVE, null)
            }.onFailure {
                releaseResourcesLocked()
                onState(ScreenCaptureState.FAILED, "CAPTURE_START_FAILED")
            }
        }
    }

    override fun stop() {
        synchronized(lifecycleLock) {
            if (!started && projection == null && virtualDisplay == null) {
                onState(ScreenCaptureState.STOPPED, null)
                return
            }
            onState(ScreenCaptureState.STOPPING, null)
            releaseResourcesLocked()
            onState(ScreenCaptureState.STOPPED, null)
        }
    }

    private fun resizeToCurrentDisplay() {
        synchronized(lifecycleLock) {
            if (!started) return
            val metrics = displayMetrics()
            val density = metrics.densityDpi.coerceAtLeast(DisplayMetrics.DENSITY_DEFAULT)
            virtualDisplay?.resize(
                metrics.widthPixels.coerceAtLeast(1),
                metrics.heightPixels.coerceAtLeast(1),
                density,
            )
        }
    }

    private fun displayMetrics(): DisplayMetrics {
        val metrics = DisplayMetrics()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val manager = context.getSystemService(WindowManager::class.java)
                ?: error("WindowManager is unavailable.")
            manager.defaultDisplay?.getRealMetrics(metrics)
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
                .defaultDisplay
                .getMetrics(metrics)
        }
        return metrics
    }

    private fun releaseResourcesLocked() {
        started = false
        componentCallbacks?.let {
            runCatching { context.unregisterComponentCallbacks(it) }
        }
        componentCallbacks = null

        virtualDisplay?.release()
        virtualDisplay = null

        imageReader?.setOnImageAvailableListener(null, null)
        imageReader?.close()
        imageReader = null

        val activeProjection = projection
        projection = null
        projectionCallback?.let { callback ->
            runCatching { activeProjection?.unregisterCallback(callback) }
        }
        projectionCallback = null
        activeProjection?.stop()
        sessionId = null
    }
}
