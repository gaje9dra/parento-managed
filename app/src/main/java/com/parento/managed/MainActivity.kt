package com.parento.managed

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModelProvider
import com.parento.managed.ui.ManagedStatusScreen
import com.parento.managed.ui.ManagedStatusViewModel

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: ManagedStatusViewModel
    private lateinit var screen: ManagedStatusScreen

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = ViewModelProvider(this)[ManagedStatusViewModel::class.java]
        screen = ManagedStatusScreen(
            context = this,
            onRetry = { viewModel.showUnenrolled() },
            onDestination = { /* Future destinations are represented by the navigation boundary. */ },
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
            screen.render(viewModel.uiState)
        }
    }
}
