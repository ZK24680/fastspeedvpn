package com.example.mywarpvpn.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.mywarpvpn.data.config.EncryptedConfigStore
import com.example.mywarpvpn.data.config.InvalidConfigException
import com.example.mywarpvpn.data.config.MissingConfigException
import com.example.mywarpvpn.domain.model.ConnectionStatus
import com.example.mywarpvpn.domain.model.TrafficStatistics
import com.example.mywarpvpn.domain.repository.VpnStateRepository
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Statistics
import com.wireguard.android.backend.Tunnel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Adapter around WireGuard's official Android tunnel library. GoBackend owns the userspace
 * WireGuard implementation, TUN interface setup, DNS/routes, and protect() calls for tunnel sockets.
 * No packet contents are exposed to this app.
 */
class WireGuardVpnEngine(
    context: Context,
    private val configStore: EncryptedConfigStore,
    private val stateRepository: VpnStateRepository,
) : VpnEngine {
    private val appContext = context.applicationContext
    private val backend = GoBackend(appContext)
    private val connectivityManager = appContext.getSystemService(ConnectivityManager::class.java)
    private val operationMutex = Mutex()

    private val tunnel = object : Tunnel {
        override fun getName(): String = TUNNEL_NAME

        override fun onStateChange(newState: Tunnel.State) {
            when (newState) {
                Tunnel.State.UP -> stateRepository.setStatus(ConnectionStatus.CONNECTED, "Tunnel established")
                Tunnel.State.DOWN -> stateRepository.setStatus(ConnectionStatus.DISCONNECTED, "Tunnel disconnected")
                Tunnel.State.TOGGLE -> Unit
            }
        }
    }

    override suspend fun connect() = operationMutex.withLock {
        connectUnderLock()
    }

    private suspend fun connectUnderLock() {
        withContext(Dispatchers.IO) {
            stateRepository.setStatus(ConnectionStatus.CONNECTING, "Connecting")
            try {
                ensureNetworkAvailable()
                val parsed = configStore.loadConfig()
                if (parsed.getPeers().isEmpty() || parsed.getInterface().getAddresses().isEmpty()) {
                    throw InvalidConfigException()
                }
                backend.setState(tunnel, Tunnel.State.UP, parsed)
                if (backend.getState(tunnel) != Tunnel.State.UP) {
                    throw TunnelStartException()
                }
                stateRepository.setStatus(ConnectionStatus.CONNECTED, "Tunnel established")
            } catch (cancelled: CancellationException) {
                runCatching { backend.setState(tunnel, Tunnel.State.DOWN, null) }
                throw cancelled
            } catch (error: Exception) {
                runCatching { backend.setState(tunnel, Tunnel.State.DOWN, null) }
                stateRepository.setStatus(ConnectionStatus.ERROR, userMessage(error))
            }
        }
    }

    override suspend fun disconnect(): Unit = operationMutex.withLock {
        withContext(Dispatchers.IO) {
            stateRepository.setStatus(ConnectionStatus.DISCONNECTING, "Disconnecting")
            try {
                backend.setState(tunnel, Tunnel.State.DOWN, null)
            } catch (_: Exception) {
                // The UI gets a stable disconnected state even when Android already revoked the VPN.
            } finally {
                stateRepository.setStatus(ConnectionStatus.DISCONNECTED)
            }
            Unit
        }
    }

    override suspend fun reconnect() = operationMutex.withLock { connectUnderLock() }

    override fun isConnected(): Boolean = try {
        backend.getState(tunnel) == Tunnel.State.UP
    } catch (_: Exception) {
        false
    }

    override suspend fun getStatistics(): TrafficStatistics = withContext(Dispatchers.IO) {
        try {
            val stats: Statistics = backend.getStatistics(tunnel)
            TrafficStatistics(receivedBytes = stats.totalRx(), sentBytes = stats.totalTx())
        } catch (_: Exception) {
            TrafficStatistics()
        }
    }

    private fun ensureNetworkAvailable() {
        val network = connectivityManager.activeNetwork ?: throw NetworkUnavailableException()
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        if (capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) != true) {
            throw NetworkUnavailableException()
        }
    }

    private fun userMessage(error: Throwable): String = when (error) {
        is MissingConfigException -> "No VPN configuration is installed. Import a WireGuard config first."
        is InvalidConfigException -> "The VPN configuration is invalid or incomplete."
        is NetworkUnavailableException -> "No network is currently available."
        else -> "The tunnel could not connect. Check the configuration, DNS, endpoint, and network."
    }

    companion object {
        const val TUNNEL_NAME = "fastspeedvpn"
    }
}

private class NetworkUnavailableException : IllegalStateException()
private class TunnelStartException : IllegalStateException()
