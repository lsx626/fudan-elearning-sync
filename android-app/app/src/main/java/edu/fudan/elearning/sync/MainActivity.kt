package edu.fudan.elearning.sync

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.fudan.elearning.sync.ui.AppViewModel
import edu.fudan.elearning.sync.ui.FudanSyncTheme
import edu.fudan.elearning.sync.ui.HomeScreen
import edu.fudan.elearning.sync.ui.LoginScreen
import edu.fudan.elearning.sync.ui.LoginState

/** 应用主入口。 */
class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermission()

        setContent {
            FudanSyncTheme {
                val viewModel: AppViewModel = viewModel()
                val loginState by viewModel.loginState.collectAsState()
                when (val state = loginState) {
                    is LoginState.LoggedIn -> HomeScreen(viewModel)
                    else -> LoginScreen(state) { username, password, remember ->
                        viewModel.login(username, password, remember)
                    }
                }
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
