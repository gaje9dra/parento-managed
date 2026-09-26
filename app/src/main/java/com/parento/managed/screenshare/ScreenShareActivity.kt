package com.parento.managed.screenshare

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.parento.managed.ParentoApplication
import kotlinx.coroutines.launch

class ScreenShareActivity : AppCompatActivity() {
    private val manager: ScreenShareManager
        get() = (application as ParentoApplication).screenShareManager

    private val authorizationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
        if (sessionId == null) {
            renderMessage("No screen-sharing session is pending.")
            return@registerForActivityResult
        }
        if (result.resultCode != Activity.RESULT_OK || result.data == null) {
            manager.publish(ScreenCaptureState.FAILED, sessionId, "AUTHORIZATION_DENIED")
            renderMessage("Screen sharing authorization was not granted.")
            return@registerForActivityResult
        }
        manager.startAfterConsent(this, result.resultCode, result.data!!, sessionId)
            .onFailure { renderMessage("Unable to start screen sharing.") }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val status = TextView(this).apply {
            textSize = 18f
            setPadding(32, 32, 32, 24)
        }
        val authorize = Button(this).apply {
            text = "Authorize screen sharing"
            setOnClickListener { requestAuthorization() }
        }
        val stop = Button(this).apply {
            text = "Stop screen sharing"
            setOnClickListener { manager.stop(intent.getStringExtra(EXTRA_SESSION_ID)) }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(status, LinearLayout.LayoutParams(-1, -2))
            addView(authorize, LinearLayout.LayoutParams(-1, -2))
            addView(stop, LinearLayout.LayoutParams(-1, -2))
        }
        setContentView(root)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                manager.state.collect { snapshot ->
                    status.text = "Screen sharing: ${snapshot.state.name}"
                    authorize.isEnabled = snapshot.state == ScreenCaptureState.AUTHORIZATION_REQUIRED ||
                        snapshot.state == ScreenCaptureState.REQUESTED
                    stop.isEnabled = snapshot.state == ScreenCaptureState.ACTIVE ||
                        snapshot.state == ScreenCaptureState.STARTING ||
                        snapshot.state == ScreenCaptureState.STOPPING
                }
            }
        }

        if (intent.action == ACTION_AUTHORIZE) requestAuthorization()
    }

    private fun requestAuthorization() {
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
        if (sessionId.isNullOrBlank()) {
            renderMessage("No screen-sharing session is pending.")
            return
        }
        lifecycleScope.launch {
            manager.requestAuthorization(sessionId)
                .onSuccess { authorizationLauncher.launch(it) }
                .onFailure { renderMessage("Screen sharing is not currently authorized for this device.") }
        }
    }

    private fun renderMessage(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()
    }

    companion object {
        const val ACTION_AUTHORIZE = "com.parento.managed.action.AUTHORIZE_SCREEN_SHARE"
        const val EXTRA_SESSION_ID = "session_id"
    }
}
