package com.example.mywarpvpn.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.mywarpvpn.MainActivity
import com.example.mywarpvpn.MyWarpApplication
import com.example.mywarpvpn.R
import com.example.mywarpvpn.domain.model.ConnectionStatus
import com.wireguard.android.backend.GoBackend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground-capable Android VpnService used by the official WireGuard GoBackend.
 * The AAR's GoBackend.VpnService is removed by manifest merge; this subclass is registered in its
 * place. Calling the superclass lifecycle publishes this service instance to GoBackend, which then
 * uses its Builder and VpnService.protect() for the WireGuard UDP sockets.
 */
class MyVpnService : GoBackend.VpnService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var connectivityManager: ConnectivityManager
    private var callbackRegistered = false
    private var reconnectAfterNetworkChange = false
    private var notificationStarted = false
    private var statsJob: Job? = null

    private val app: MyWarpApplication
        get() = application as MyWarpApplication

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onLost(network: Network) {
            if (app.vpnEngine.isConnected()) {
                reconnectAfterNetworkChange = true
                app.stateRepository.setStatus(
                    ConnectionStatus.ERROR,
                    "Network unavailable. Waiting for a network to return.",
                )
            }
        }

        override fun onAvailable(network: Network) {
            if (!reconnectAfterNetworkChange) return
            reconnectAfterNetworkChange = false
            serviceScope.launch {
                delay(NETWORK_RECONNECT_DELAY_MS)
                app.stateRepository.appendLog("Network restored; reconnecting")
                app.vpnEngine.reconnect()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        promoteToForeground()

        connectivityManager = getSystemService(ConnectivityManager::class.java)
        try {
            connectivityManager.registerDefaultNetworkCallback(networkCallback)
            callbackRegistered = true
        } catch (_: RuntimeException) {
            app.stateRepository.appendLog("Network monitoring is unavailable")
        }

        serviceScope.launch {
            while (isActive) {
                if (app.vpnEngine.isConnected()) {
                    app.stateRepository.setStatistics(app.vpnEngine.getStatistics())
                }
                delay(STATISTICS_INTERVAL_MS)
            }
        }
        serviceScope.launch {
            app.stateRepository.state.collect { state ->
                if (notificationStarted) {
                    getSystemService(NotificationManager::class.java)
                        .notify(NOTIFICATION_ID, buildNotification(state.status))
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // super handles GoBackend's service registration and Android's always-on callback hook.
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_DISCONNECT -> serviceScope.launch {
                app.vpnEngine.disconnect()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }

            ACTION_CONNECT -> startTunnel(startId)

            else -> {
                // Android starts the selected VpnService with an implicit or null intent when
                // Always-on VPN is enabled. Use the previously imported encrypted config.
                startTunnel(startId)
            }
        }
        return START_STICKY
    }

    override fun onRevoke() {
        app.stateRepository.setStatus(ConnectionStatus.ERROR, "VPN permission was revoked in Android settings.")
        serviceScope.launch {
            app.vpnEngine.disconnect()
            stopSelf()
        }
    }

    override fun onDestroy() {
        if (callbackRegistered) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback)
            } catch (_: RuntimeException) {
                // Android may have already removed callbacks during service teardown.
            }
        }
        statsJob?.cancel()
        serviceScope.cancel()
        if (notificationStarted) stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun startTunnel(startId: Int) {
        statsJob?.cancel()
        statsJob = serviceScope.launch {
            app.vpnEngine.connect()
            if (app.stateRepository.state.value.status == ConnectionStatus.ERROR) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
        }
    }

    private fun promoteToForeground() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(ConnectionStatus.CONNECTING),
            type,
        )
        notificationStarted = true
    }

    private fun buildNotification(status: ConnectionStatus): Notification {
        val message = when (status) {
            ConnectionStatus.CONNECTED -> getString(R.string.notification_connected)
            ConnectionStatus.CONNECTING -> "Connecting to VPN endpoint"
            ConnectionStatus.DISCONNECTING -> "Disconnecting VPN"
            ConnectionStatus.ERROR -> "VPN needs attention"
            ConnectionStatus.DISCONNECTED -> "VPN service is ready"
        }
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_vpn)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(message)
            .setContentIntent(openApp)
            .setOngoing(status == ConnectionStatus.CONNECTING || status == ConnectionStatus.CONNECTED)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.notification_channel_description)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        const val ACTION_CONNECT = "com.example.mywarpvpn.action.CONNECT"
        const val ACTION_DISCONNECT = "com.example.mywarpvpn.action.DISCONNECT"
        private const val NOTIFICATION_CHANNEL_ID = "vpn_connection"
        private const val NOTIFICATION_ID = 1001
        private const val STATISTICS_INTERVAL_MS = 1_000L
        private const val NETWORK_RECONNECT_DELAY_MS = 800L

        fun start(context: Context, action: String) {
            val intent = Intent(context, MyVpnService::class.java).setAction(action)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
