package com.example.mywarpvpn.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.example.mywarpvpn.domain.model.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appSettingsDataStore by preferencesDataStore(name = "app_settings")

class PreferencesRepository(private val context: Context) {
    val settings: Flow<AppSettings> = context.appSettingsDataStore.data.map { values ->
        AppSettings(autoConnectOnAppOpen = values[AUTO_CONNECT] ?: false)
    }

    suspend fun setAutoConnectOnAppOpen(enabled: Boolean) {
        context.appSettingsDataStore.edit { values -> values[AUTO_CONNECT] = enabled }
    }

    companion object {
        private val AUTO_CONNECT = booleanPreferencesKey("auto_connect_on_app_open")
    }
}
