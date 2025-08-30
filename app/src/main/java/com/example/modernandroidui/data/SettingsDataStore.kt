package com.example.modernandroidui.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.settingsDataStore by preferencesDataStore(name = "settings")

object SettingsKeys {
    val GEOFENCING_ENABLED = booleanPreferencesKey("geofencing_enabled")
}

class SettingsDataStore(private val context: Context) {
    val geofencingEnabledFlow: Flow<Boolean> =
        context.settingsDataStore.data.map { prefs ->
            prefs[SettingsKeys.GEOFENCING_ENABLED] ?: false
        }

    suspend fun setGeofencingEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[SettingsKeys.GEOFENCING_ENABLED] = enabled
        }
    }
}
