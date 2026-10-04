package com.example.mywarpvpn.vpn

import com.example.mywarpvpn.domain.model.TrafficStatistics

interface VpnEngine {
    suspend fun connect()
    suspend fun disconnect()
    suspend fun reconnect()
    fun isConnected(): Boolean
    suspend fun getStatistics(): TrafficStatistics
}
