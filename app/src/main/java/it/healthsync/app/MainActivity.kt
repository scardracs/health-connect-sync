package it.healthsync.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import it.healthsync.app.ui.SettingsScreen
import it.healthsync.app.ui.SettingsViewModel
import it.healthsync.app.ui.theme.SaluteSyncTheme

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(wrapItalian(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SaluteSyncTheme {
                val viewModel: SettingsViewModel = viewModel()
                SettingsScreen(viewModel)
            }
        }
    }
}
