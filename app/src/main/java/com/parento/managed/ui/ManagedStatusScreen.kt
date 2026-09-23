package com.parento.managed.ui

import android.content.Context
import android.graphics.Typeface
import android.view.View
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
    }

    private val title = MaterialTextView(context).apply {
        text = context.getString(R.string.app_name)
        textSize = 28f
        setTypeface(typeface, Typeface.BOLD)
    }

    private val subtitle = MaterialTextView(context).apply {
        textSize = 16f
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
        content.addView(
            LinearProgressIndicator(context).apply {
                isIndeterminate = true
                contentDescription = context.getString(R.string.loading_description)
            },
            LinearLayout.LayoutParams(-1, -2),
        )
    }

    private fun renderUnenrolled() {
        subtitle.text = context.getString(R.string.unenrolled_state)
        addMessage(context.getString(R.string.version_label, BuildConfig.VERSION_NAME))
        addMessage(context.getString(R.string.unenrolled_message))
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
            addAction(context.getString(R.string.retry))
        }
    }

    private fun addMessage(message: String) {
        content.addView(
            MaterialTextView(context).apply {
                text = message
                textSize = 17f
            },
            LinearLayout.LayoutParams(-1, -2),
        )
    }

    private fun addAction(text: String) {
        content.addView(
            com.google.android.material.button.MaterialButton(context).apply {
                this.text = text
                setOnClickListener { onRetry() }
                minHeight = context.resources.getDimensionPixelSize(R.dimen.minimum_touch_target)
            },
            LinearLayout.LayoutParams(-1, -2),
        )
    }
}
