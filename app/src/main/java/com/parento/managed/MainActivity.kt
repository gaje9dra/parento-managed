package com.parento.managed

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.activity.result.contract.ActivityResultContracts
import com.parento.managed.domain.ConnectionState
import com.parento.managed.domain.EnrollmentState
import com.parento.managed.domain.OperationResult
import com.parento.managed.enrollment.EnrollmentScreen
import com.parento.managed.enrollment.EnrollmentViewModel
import com.parento.managed.enrollment.EnrollmentViewModelFactory
import com.parento.managed.ui.ManagedStatusScreen
import com.parento.managed.ui.ManagedStatusViewModel
import com.parento.managed.monitoring.RoomMonitoringRepository
import android.Manifest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var statusViewModel: ManagedStatusViewModel
    private lateinit var enrollmentViewModel: EnrollmentViewModel
    private lateinit var statusScreen: ManagedStatusScreen
    private lateinit var enrollmentScreen: EnrollmentScreen
    private lateinit var enrollmentContainer: LinearLayout

    private val foregroundLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { app?.let { refreshLocationUi(it) } }

    private var app: ParentoApplication? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        val app = application as ParentoApplication
        this.app = app
        statusViewModel = ViewModelProvider(this)[ManagedStatusViewModel::class.java]
        enrollmentViewModel = ViewModelProvider(this, EnrollmentViewModelFactory(app.enrollmentRepository))[EnrollmentViewModel::class.java]

        statusScreen = ManagedStatusScreen(this, { app.retryInitialization() }) { requestLocationAccess() }
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
                                statusViewModel.showManagementState(state, state.enrollmentState, app.connectionState.value, locationCapability = app.locationCoordinator.capability())
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
                            statusViewModel.showManagementState(current.value, current.value.enrollmentState, app.connectionState.value, snapshot, app.locationCoordinator.capability())
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
                                app.locationCoordinator.capability(),
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
    private fun requestLocationAccess() {
        if (app?.locationCoordinator?.capability() == com.parento.managed.location.LocationCapabilityState.PERMISSION_REQUIRED) {
            foregroundLocationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            )
        } else if (app?.locationCoordinator?.capability() == com.parento.managed.location.LocationCapabilityState.AVAILABLE) {
            return
        } else {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }
    }

    private fun refreshLocationUi(app: ParentoApplication) {
        val current = app.managementInitialization.value
        if (current is OperationResult.Success) {
            statusViewModel.showManagementState(
                current.value,
                current.value.enrollmentState,
                app.connectionState.value,
                locationCapability = app.locationCoordinator.capability(),
            )
            statusScreen.render(statusViewModel.uiState)
        }
    }

    override fun onResume() {
        super.onResume()
        app?.let { refreshLocationUi(it) }
    }

}
