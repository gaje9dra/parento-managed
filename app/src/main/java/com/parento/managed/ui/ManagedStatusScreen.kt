package com.parento.managed.ui

import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import androidx.core.view.setPadding
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textview.MaterialTextView
import com.parento.managed.R
import com.parento.managed.navigation.RootDestination

class ManagedStatusScreen(
    context: Context,
    private val onRetry: () -> Unit,
    private val onDestination: (RootDestination) -> Unit,
) {
    private val root = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(context.resources.getDimensionPixelSize(R.dimen.screen_padding))
        setBackgroundColor(context.getColor(android.R.color.background_light))
    }

    private val title = MaterialTextView(context).apply {
        text = context.getString(R.string.app_name)
        textSize = 28f
        setTypeface(typeface, Typeface.BOLD)
    }

    private val subtitle = MaterialTextView(context).apply {
        textSize = 16f
        setPadding(0, 12, 0, 20)
    }

    private val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
    }

    init {
        root.addView(title, LinearLayout.LayoutParams(-1, -2))
        root.addView(subtitle, LinearLayout.LayoutParams(-1, -2))
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
    }

    fun view(): View = root

    fun render(state: ManagedUiState) {
        content.removeAllViews()

        when (state) {
            ManagedUiState.Loading -> renderLoading()
            ManagedUiState.Unenrolled -> renderUnenrolled()
            is ManagedUiState.Content -> renderContent(state)
            is ManagedUiState.Error -> renderError(state)
        }
    }

    private fun renderLoading() {
        subtitle.text = context.getString(R.string.loading_state)
        val progress = LinearProgressIndicator(context).apply {
            isIndeterminate = true
            contentDescription = context.getString(R.string.loading_description)
        }
        content.addView(progress, LinearLayout.LayoutParams(-1, -2))
    }

    private fun renderUnenrolled() {
        subtitle.text = context.getString(R.string.unenrolled_state)
        addMessage(context.getString(R.string.unenrolled_message))
        addAction(
            text = context.getString(R.string.enrollment_placeholder_action),
            enabled = false,
        )
        onDestination(RootDestination.DEVICE_STATUS)
    }

    private fun renderContent(state: ManagedUiState.Content) {
        subtitle.text = context.getString(R.string.device_status_title)
        addMessage(state.managementLabel)
        addMessage(state.connectionLabel)
        addMessage(context.getString(R.string.no_device_features_message))
    }

    private fun renderError(state: ManagedUiState.Error) {
        subtitle.text = context.getString(R.string.error_state)
        addMessage(state.message)
        if (state.canRetry) {
            addAction(context.getString(R.string.retry), enabled = true)
        }
    }

    private fun addMessage(message: String) {
        content.addView(
            MaterialTextView(context).apply {
                text = message
                textSize = 17f
                setPadding(0, 8, 0, 8)
            },
            LinearLayout.LayoutParams(-1, -2),
        )
    }

    private fun addAction(text: String, enabled: Boolean) {
        content.addView(
            Button(context).apply {
                this.text = text
                isEnabled = enabled
                setOnClickListener { if (enabled) onRetry() }
                minHeight = context.resources.getDimensionPixelSize(R.dimen.minimum_touch_target)
            },
            LinearLayout.LayoutParams(-1, -2),
        )
    }
}
