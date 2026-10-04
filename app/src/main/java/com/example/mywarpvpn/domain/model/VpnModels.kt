package com.example.mywarpvpn.domain.model

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    ERROR,
}

data class ConfigSummary(
    val endpoint: String,
    val dnsServers: String,
    val mtu: String,
    val peerCount: Int,
)

data class TrafficStatistics(
    val receivedBytes: Long = 0,
    val sentBytes: Long = 0,
)

data class VpnUiState(
    val status: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val connectedSinceMillis: Long? = null,
    val statistics: TrafficStatistics = TrafficStatistics(),
    val errorMessage: String? = null,
    val configSummary: ConfigSummary? = null,
)

data class ConnectionLogEntry(
    val timestampMillis: Long,
    val message: String,
)

data class AppSettings(
    val autoConnectOnAppOpen: Boolean = false,
)
