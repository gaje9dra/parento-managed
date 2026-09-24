package com.parento.managed

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.parento.managed.domain.ConnectionState
import com.parento.managed.domain.OperationResult
import com.parento.managed.ui.ManagedStatusScreen
import com.parento.managed.ui.ManagedStatusViewModel
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var viewModel: ManagedStatusViewModel
    private lateinit var screen: ManagedStatusScreen

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = androidx.lifecycle.ViewModelProvider(this)[ManagedStatusViewModel::class.java]
        screen = ManagedStatusScreen(
            context = this,
            onRetry = {
                (application as ParentoApplication).retryInitialization()
            },
        )

        setContentView(screen.view())
        viewModel.showLoading()
        screen.render(viewModel.uiState)

        val app = application as ParentoApplication
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                app.managementInitialization.collect { result ->
                    when (result) {
                        null -> viewModel.showLoading()
                        is OperationResult.Failure ->
                            viewModel.showError(
                                error = result.error,
                                message = "Managed-device state is currently unavailable.",
                                canRetry = app.initializationState.value.status !=
                                    com.parento.managed.lifecycle.InitializationStatus.INITIALIZING,
                            )

                        is OperationResult.Success -> {
                            val state = result.value
                            viewModel.showManagementState(
                                managementState = state,
                                enrollmentState = state.enrollmentState,
                                connectionState = ConnectionState.UNKNOWN,
                            )
                        }
                    }
                    screen.render(viewModel.uiState)
                }
            }
        }
    }
}