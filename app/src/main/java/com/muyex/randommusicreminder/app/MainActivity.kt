package com.muyex.randommusicreminder.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.muyex.randommusicreminder.ui.AppViewModel
import com.muyex.randommusicreminder.ui.ToolsViewModel
import com.muyex.randommusicreminder.ui.LexingApp
import com.muyex.randommusicreminder.ui.theme.LexingTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue

class MainActivity : ComponentActivity() {
    private val container get() = (application as LexingApplication).container
    private val appViewModel: AppViewModel by viewModels {
        AppViewModel.Factory((application as LexingApplication).container)
    }
    private val toolsViewModel: ToolsViewModel by viewModels { ToolsViewModel.Factory(container) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by appViewModel.uiState.collectAsStateWithLifecycle()
            com.muyex.randommusicreminder.ui.theme.SystemBarAppearance(this, state.settings.appearance)
            LexingTheme(state.settings.appearance) {
                LexingApp(appViewModel, toolsViewModel)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        container.usageTracker.setVisible(true)
    }

    override fun onStop() {
        if (!isChangingConfigurations) container.usageTracker.setVisible(false)
        super.onStop()
    }
}
