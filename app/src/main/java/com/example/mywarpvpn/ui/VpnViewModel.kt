package com.example.mywarpvpn.ui

import android.net.VpnService
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mywarpvpn.MyWarpApplication
import com.example.mywarpvpn.data.config.InvalidConfigException
import com.example.mywarpvpn.data.warp.WarpRegistrationException
import com.example.mywarpvpn.domain.model.AppSettings
import com.example.mywarpvpn.domain.model.ConnectionLogEntry
import com.example.mywarpvpn.domain.model.ConfigSummary
import com.example.mywarpvpn.domain.model.VpnUiState
import com.example.mywarpvpn.domain.repository.VpnStateRepository
import com.example.mywarpvpn.data.preferences.PreferencesRepository
import com.example.mywarpvpn.service.MyVpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class VpnScreenState(
    val vpn: VpnUiState = VpnUiState(),
    val logs: List<ConnectionLogEntry> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val busy: Boolean = false,
    val feedback: String? = null,
)

class VpnViewModel(
    private val app: MyWarpApplication,
    private val stateRepository: VpnStateRepository,
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {
    private val busy = MutableStateFlow(false)
    private val feedback = MutableStateFlow<String?>(null)
    private val mutableUiState = MutableStateFlow(VpnScreenState())
    val uiState = mutableUiState

    init {
        viewModelScope.launch {
            combine(stateRepository.state, stateRepository.logs, preferencesRepository.settings, busy, feedback) {
                    vpn, logs, settings, isBusy, message ->
                VpnScreenState(vpn, logs, settings, isBusy, message)
            }.collect { mutableUiState.value = it }
        }
        viewModelScope.launch {
            val summary = withContext(Dispatchers.IO) {
                try {
                    app.configStore.describeConfig()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
            }
            stateRepository.setConfigSummary(summary)
            val settings = preferencesRepository.settings.first()
            if (summary != null && settings.autoConnectOnAppOpen &&
                VpnService.prepare(app) == null && !app.vpnEngine.isConnected()
            ) {
                MyVpnService.start(app, MyVpnService.ACTION_CONNECT)
            }
        }
    }

    fun connect() {
        if (app.configStore.hasConfig().not()) {
            feedback.value = "Set up WARP or import a WireGuard configuration before connecting."
            return
        }
        feedback.value = null
        MyVpnService.start(app, MyVpnService.ACTION_CONNECT)
    }

    fun disconnect() {
        feedback.value = null
        MyVpnService.start(app, MyVpnService.ACTION_DISCONNECT)
    }

    fun permissionDenied() {
        stateRepository.setStatus(
            com.example.mywarpvpn.domain.model.ConnectionStatus.ERROR,
            "VPN permission was denied. Android requires your approval to create the VPN interface.",
        )
    }

    fun importConfig(uri: Uri) {
        viewModelScope.launch {
            busy.value = true
            try {
                val summary = withContext(Dispatchers.IO) {
                    when (stateRepository.state.value.status) {
                        com.example.mywarpvpn.domain.model.ConnectionStatus.CONNECTING,
                        com.example.mywarpvpn.domain.model.ConnectionStatus.CONNECTED,
                        com.example.mywarpvpn.domain.model.ConnectionStatus.DISCONNECTING -> {
                            app.vpnEngine.disconnect()
                        }
                        else -> Unit
                    }
                    app.configStore.importFrom(uri)
                }
                stateRepository.setConfigSummary(summary)
                stateRepository.setStatus(
                    com.example.mywarpvpn.domain.model.ConnectionStatus.DISCONNECTED,
                    "Encrypted WireGuard configuration imported",
                )
                feedback.value = "Configuration imported and encrypted on this device."
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val message = if (error is InvalidConfigException) {
                    "That file is not a complete WireGuard client configuration."
                } else {
                    "The configuration file could not be read or saved."
                }
                feedback.value = message
                stateRepository.appendLog(message)
            } finally {
                busy.value = false
            }
        }
    }

    fun setUpWarp() {
        if (busy.value) return
        feedback.value = null
        viewModelScope.launch {
            busy.value = true
            try {
                val summary = withContext(Dispatchers.IO) {
                    val configText = app.warpRegistrar.createWireGuardConfig()
                    app.configStore.importConfigText(configText)
                }
                stateRepository.setConfigSummary(summary)
                stateRepository.setStatus(
                    com.example.mywarpvpn.domain.model.ConnectionStatus.DISCONNECTED,
                    "Experimental Cloudflare WARP profile created",
                )
                feedback.value = "WARP profile created and encrypted on this device. Tap Connect to start."
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val message = when ((error as? WarpRegistrationException)?.httpStatus) {
                    429 -> "Cloudflare is rate-limiting WARP setup. Wait a few minutes and try again."
                    401, 403 -> "Cloudflare rejected this experimental WARP setup. Its registration service may have changed."
                    else -> "WARP setup failed. Check your internet connection; Cloudflare may block this unsupported registration method."
                }
                feedback.value = message
                stateRepository.appendLog(message)
            } finally {
                busy.value = false
            }
        }
    }

    fun setAutoConnect(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setAutoConnectOnAppOpen(enabled)
            feedback.value = if (enabled) {
                "Auto-connect will run when the app opens and Android VPN permission is already granted."
            } else {
                "Auto-connect on app open is off."
            }
        }
    }

    fun clearFeedback() {
        feedback.value = null
    }

    companion object {
        fun factory(app: MyWarpApplication): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (!modelClass.isAssignableFrom(VpnViewModel::class.java)) {
                        throw IllegalArgumentException("Unknown ViewModel class")
                    }
                    return VpnViewModel(app, app.stateRepository, app.preferencesRepository) as T
                }
            }
    }
}
