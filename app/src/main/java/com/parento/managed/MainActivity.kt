package com.parento.managed

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.parento.managed.domain.ConnectionState
import com.parento.managed.domain.EnrollmentState
import com.parento.managed.domain.OperationResult
import com.parento.managed.enrollment.EnrollmentScreen
import com.parento.managed.enrollment.EnrollmentViewModel
import com.parento.managed.enrollment.EnrollmentViewModelFactory
import com.parento.managed.ui.ManagedStatusScreen
import com.parento.managed.ui.ManagedStatusViewModel
import com.parento.managed.monitoring.RoomMonitoringRepository
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var statusViewModel: ManagedStatusViewModel
    private lateinit var enrollmentViewModel: EnrollmentViewModel
    private lateinit var statusScreen: ManagedStatusScreen
    private lateinit var enrollmentScreen: EnrollmentScreen
    private lateinit var enrollmentContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        val app = application as ParentoApplication
        statusViewModel = ViewModelProvider(this)[ManagedStatusViewModel::class.java]
        enrollmentViewModel = ViewModelProvider(this, EnrollmentViewModelFactory(app.enrollmentRepository))[EnrollmentViewModel::class.java]

        statusScreen = ManagedStatusScreen(this) { app.retryInitialization() }
        enrollmentScreen = EnrollmentScreen(
            context = this,
            onStart = { id, secret, expiresAt, _ -> enrollmentViewModel.start(id, secret, expiresAt) },
            onEnroll = { name -> enrollmentViewModel.enroll(name) },
            onCancel = { enrollmentViewModel.cancel() },
        )

        enrollmentContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(enrollmentScreen.view(), LinearLayout.LayoutParams(-1, -2))
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(statusScreen.view(), LinearLayout.LayoutParams(-1, 0, 1f))
            addView(enrollmentContainer, LinearLayout.LayoutParams(-1, -2))
        }
        setContentView(root)

        statusViewModel.showLoading()
        statusScreen.render(statusViewModel.uiState)
        enrollmentViewModel.restore()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    app.managementInitialization.collect { result ->
                        when (result) {
                            null -> statusViewModel.showLoading()
                            is OperationResult.Failure -> statusViewModel.showError(result.error, "Managed-device state is currently unavailable.", true)
                            is OperationResult.Success -> {
                                val state = result.value
                                statusViewModel.showManagementState(state, state.enrollmentState, app.connectionState.value)
                                enrollmentContainer.visibility = if (state.enrollmentState == EnrollmentState.ENROLLED) View.GONE else View.VISIBLE
                            }
                        }
                        statusScreen.render(statusViewModel.uiState)
                    }
                }
                launch {
                    RoomMonitoringRepository(com.parento.managed.data.local.LocalDatabaseProvider.get().monitoringSnapshotDao()).observe().collect { monitoring ->
                        val current = app.managementInitialization.value
                        if (current is OperationResult.Success) {
                            val snapshot = (monitoring as? OperationResult.Success)?.value
                            statusViewModel.showManagementState(current.value, current.value.enrollmentState, app.connectionState.value, snapshot)
                            statusScreen.render(statusViewModel.uiState)
                        }
                    }
                }
                launch {
                    app.connectionState.collect { connection ->
                        val current = app.managementInitialization.value
                        if (current is OperationResult.Success) {
                            statusViewModel.showManagementState(
                                current.value,
                                current.value.enrollmentState,
                                connection,
                                null,
                            )
                            statusScreen.render(statusViewModel.uiState)
                        }
                    }
                }
                launch {
                    enrollmentViewModel.state.collect { state ->
                        enrollmentScreen.setBusy(state.busy)
                        enrollmentScreen.setPending(state.pending)
                        enrollmentScreen.setMessage(state.message)
                        if (state.message == "Enrollment completed.") app.retryInitialization()
                    }
                }
            }
        }
    }
}
