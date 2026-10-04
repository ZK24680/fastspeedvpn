package com.example.mywarpvpn.domain.repository

import com.example.mywarpvpn.domain.model.ConnectionLogEntry
import com.example.mywarpvpn.domain.model.ConnectionStatus
import com.example.mywarpvpn.domain.model.TrafficStatistics
import com.example.mywarpvpn.domain.model.VpnUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class VpnStateRepository {
    private val mutableState = MutableStateFlow(VpnUiState())
    val state: StateFlow<VpnUiState> = mutableState.asStateFlow()

    private val mutableLogs = MutableStateFlow<List<ConnectionLogEntry>>(emptyList())
    val logs: StateFlow<List<ConnectionLogEntry>> = mutableLogs.asStateFlow()

    fun setConfigSummary(summary: com.example.mywarpvpn.domain.model.ConfigSummary?) {
        mutableState.value = mutableState.value.copy(configSummary = summary)
    }

    fun setStatus(status: ConnectionStatus, message: String? = null) {
        val previous = mutableState.value
        val connectedAt = when (status) {
            ConnectionStatus.CONNECTED -> previous.connectedSinceMillis ?: System.currentTimeMillis()
            else -> null
        }
        mutableState.value = previous.copy(
            status = status,
            connectedSinceMillis = connectedAt,
            errorMessage = if (status == ConnectionStatus.ERROR) message else null,
            statistics = if (status == ConnectionStatus.DISCONNECTED || status == ConnectionStatus.ERROR) {
                TrafficStatistics()
            } else {
                previous.statistics
            },
        )
        if (message != null) appendLog(message)
    }

    fun setStatistics(statistics: TrafficStatistics) {
        if (mutableState.value.status == ConnectionStatus.CONNECTED) {
            mutableState.value = mutableState.value.copy(statistics = statistics)
        }
    }

    fun appendLog(message: String) {
        val entry = ConnectionLogEntry(System.currentTimeMillis(), message)
        mutableLogs.value = (mutableLogs.value + entry).takeLast(MAX_LOG_ENTRIES)
    }

    companion object {
        private const val MAX_LOG_ENTRIES = 80
    }
}
