package com.wevans.caandroidnessusfrontend.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "nessus_settings")

data class NessusSettings(
    val baseUrl: String = "",
    val accessKey: String = "",
    val secretKey: String = ""
)

class SettingsStore(private val context: Context) {
    private val baseUrlKey = stringPreferencesKey("base_url")
    private val accessKey = stringPreferencesKey("access_key")
    private val secretKey = stringPreferencesKey("secret_key")

    val settings: Flow<NessusSettings> = context.dataStore.data.map { prefs ->
        NessusSettings(
            baseUrl = prefs[baseUrlKey].orEmpty(),
            accessKey = prefs[accessKey].orEmpty(),
            secretKey = prefs[secretKey].orEmpty()
        )
    }

    suspend fun save(settings: NessusSettings) {
        context.dataStore.edit { prefs ->
            prefs[baseUrlKey] = settings.baseUrl.trim()
            prefs[accessKey] = settings.accessKey.trim()
            prefs[secretKey] = settings.secretKey.trim()
        }
    }
}
