package com.example.mywarpvpn

import android.app.Application
import com.example.mywarpvpn.data.config.EncryptedConfigStore
import com.example.mywarpvpn.data.preferences.PreferencesRepository
import com.example.mywarpvpn.data.warp.CloudflareWarpRegistrar
import com.example.mywarpvpn.domain.repository.VpnStateRepository
import com.example.mywarpvpn.vpn.VpnEngine
import com.example.mywarpvpn.vpn.WireGuardVpnEngine

class MyWarpApplication : Application() {
    val stateRepository by lazy { VpnStateRepository() }
    val configStore by lazy { EncryptedConfigStore(this) }
    val warpRegistrar by lazy { CloudflareWarpRegistrar() }
    val preferencesRepository by lazy { PreferencesRepository(this) }
    val vpnEngine: VpnEngine by lazy { WireGuardVpnEngine(this, configStore, stateRepository) }
}
