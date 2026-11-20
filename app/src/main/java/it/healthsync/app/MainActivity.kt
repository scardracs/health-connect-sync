package it.healthsync.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import it.healthsync.app.ui.SaluteApp
import it.healthsync.app.ui.SettingsViewModel
import it.healthsync.app.ui.theme.SaluteSyncTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SaluteSyncTheme {
                val viewModel: SettingsViewModel = viewModel()
                SaluteApp(viewModel)
            }
        }
    }
}
