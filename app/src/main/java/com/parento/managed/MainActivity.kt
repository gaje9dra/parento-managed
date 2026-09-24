package com.parento.managed

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.parento.managed.ui.ManagedStatusScreen
import com.parento.managed.ui.ManagedStatusViewModel

class MainActivity : AppCompatActivity() {
    private lateinit var viewModel: ManagedStatusViewModel
    private lateinit var screen: ManagedStatusScreen

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = ViewModelProvider(this)[ManagedStatusViewModel::class.java]
        screen = ManagedStatusScreen(
            context = this,
            onRetry = {
                viewModel.showUnenrolled()
                render()
            },
        )

        setContentView(screen.view())
        render()
    }

    override fun onStart() {
        super.onStart()
        render()
    }

    private fun render() {
        if (::screen.isInitialized && ::viewModel.isInitialized) {
            val app = application as ParentoApplication
            app.managementState?.let { state ->
                viewModel.showManagementState(
                    managementState = state,
                    enrollmentState = com.parento.managed.domain.EnrollmentState.UNENROLLED,
                    connectionState = com.parento.managed.domain.ConnectionState.UNKNOWN,
                )
            }
            screen.render(viewModel.uiState)
        }
    }
}
