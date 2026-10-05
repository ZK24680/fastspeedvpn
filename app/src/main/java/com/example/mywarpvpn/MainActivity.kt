package com.example.mywarpvpn

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import com.example.mywarpvpn.ui.MyWarpVpnApp
import com.example.mywarpvpn.ui.VpnViewModel

private const val ADSTERRA_SMARTLINK_URL =
    "https://www.profitableratecpmnetwork.com/q1wh01fp2g?key=0fe8dff9dc272bef1d77497916aa4493"

class MainActivity : ComponentActivity() {
    private val viewModel: VpnViewModel by viewModels {
        VpnViewModel.factory(application as MyWarpApplication)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MainContent(viewModel) }
    }
}

@Composable
private fun MainContent(viewModel: VpnViewModel) {
    val context = LocalContext.current
    val screenState by viewModel.uiState.collectAsState()
    var awaitingNotificationResponse by remember { mutableStateOf(false) }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) viewModel.connect() else viewModel.permissionDenied()
    }

    fun requestVpnPermission() {
        val permissionIntent = VpnService.prepare(context)
        if (permissionIntent == null) viewModel.connect() else vpnPermissionLauncher.launch(permissionIntent)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        awaitingNotificationResponse = false
        // Notification permission is optional for the tunnel; continue either way.
        requestVpnPermission()
    }

    val configPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (_: SecurityException) {
                // The config is read immediately; a persistent grant is only a convenience.
            }
            viewModel.importConfig(uri)
        }
    }

    fun onConnectPressed() {
        val notificationPermissionMissing = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (notificationPermissionMissing && !awaitingNotificationResponse) {
            awaitingNotificationResponse = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            requestVpnPermission()
        }
    }

    fun openAndroidVpnSettings() {
        try {
            context.startActivity(Intent(Settings.ACTION_VPN_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            context.startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    MyWarpVpnApp(
        state = screenState,
        onConnect = { onConnectPressed() },
        onDisconnect = viewModel::disconnect,
        onImport = { configPicker.launch(arrayOf("*/*")) },
        onSetupWarp = viewModel::setUpWarp,
        onOpenWarpTerms = {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.cloudflare.com/application/terms/")))
            } catch (_: ActivityNotFoundException) {
                viewModel.clearFeedback()
            }
        },
        onOpenAd = {
            try {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, android.net.Uri.parse(ADSTERRA_SMARTLINK_URL)),
                )
            } catch (_: ActivityNotFoundException) {
                // No browser or compatible activity is installed.
            }
        },
        onAutoConnectChanged = viewModel::setAutoConnect,
        onOpenVpnSettings = { openAndroidVpnSettings() },
        onFeedbackShown = viewModel::clearFeedback,
    )
}
