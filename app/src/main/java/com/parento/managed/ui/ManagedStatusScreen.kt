package com.parento.managed.ui

import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.widget.LinearLayout
import androidx.core.view.setPadding
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textview.MaterialTextView
import com.parento.managed.BuildConfig
import com.parento.managed.R
import com.parento.managed.monitoring.MonitoringSnapshot

class ManagedStatusScreen(private val context: Context, private val onRetry: () -> Unit) {
    private val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(context.resources.getDimensionPixelSize(R.dimen.screen_padding)) }
    private val title = MaterialTextView(context).apply { text = context.getString(R.string.app_name); textSize = 28f; setTypeface(typeface, Typeface.BOLD) }
    private val subtitle = MaterialTextView(context).apply { textSize = 16f }
    private val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    init { root.addView(title, LinearLayout.LayoutParams(-1,-2)); root.addView(subtitle, LinearLayout.LayoutParams(-1,-2)); root.addView(content, LinearLayout.LayoutParams(-1,0,1f)) }
    fun view(): View = root
    fun render(state: ManagedUiState) { content.removeAllViews(); when(state) { ManagedUiState.Loading -> renderLoading(); ManagedUiState.Unenrolled -> renderUnenrolled(); is ManagedUiState.Content -> renderContent(state); is ManagedUiState.Error -> renderError(state) } }
    private fun renderLoading() { subtitle.text=context.getString(R.string.loading_state); content.addView(LinearProgressIndicator(context).apply { isIndeterminate=true; contentDescription=context.getString(R.string.loading_description) },LinearLayout.LayoutParams(-1,-2)) }
    private fun renderUnenrolled() { subtitle.text=context.getString(R.string.unenrolled_state); addMessage(context.getString(R.string.version_label,BuildConfig.VERSION_NAME)); addMessage(context.getString(R.string.unenrolled_message)) }
    private fun renderContent(state: ManagedUiState.Content) { subtitle.text=context.getString(R.string.device_status_title); addMessage(context.getString(R.string.management_mode_title)); addMessage(state.managementLabel); addMessage(context.getString(R.string.enrollment_state_title)); addMessage(state.deviceStatus.name); addMessage(context.getString(R.string.connection_state_title)); addMessage(state.connectionLabel); state.monitoringSnapshot?.let(::renderMonitoring) }
    private fun renderMonitoring(snapshot: MonitoringSnapshot) {
        addMessage("Android: " + snapshot.deviceInfo.androidVersion + " (API " + snapshot.deviceInfo.apiLevel + ")")
        addMessage("App: " + snapshot.deviceInfo.appVersion + " (" + snapshot.deviceInfo.appVersionCode + ")")
        addMessage("Battery: " + (snapshot.battery.percentage?.let { it.toString() + "%" } ?: "Unavailable") + " · " + snapshot.battery.chargingState)
        addMessage("Network: " + snapshot.network.state)
        addMessage("Storage: " + formatBytes(snapshot.storage.availableBytes) + " available / " + formatBytes(snapshot.storage.totalBytes) + " total")
        addMessage("Memory: " + formatBytes(snapshot.memory.availableBytes) + " available / " + formatBytes(snapshot.memory.totalBytes) + " total")
        addMessage("Last monitoring update: " + snapshot.lastMonitoringUpdateEpochMillis)
    }
    private fun formatBytes(value: Long?): String = value?.let { (it / (1024L * 1024L)).toString() + " MB" } ?: "Unavailable"
    private fun renderError(state: ManagedUiState.Error) { subtitle.text=context.getString(R.string.error_state); addMessage(state.message); if(state.canRetry) addAction(context.getString(R.string.retry)) }
    private fun addMessage(message:String) { content.addView(MaterialTextView(context).apply { text=message; textSize=17f },LinearLayout.LayoutParams(-1,-2)) }
    private fun addAction(text:String) { content.addView(com.google.android.material.button.MaterialButton(context).apply { this.text=text; setOnClickListener { onRetry() }; minHeight=context.resources.getDimensionPixelSize(R.dimen.minimum_touch_target) },LinearLayout.LayoutParams(-1,-2)) }
}