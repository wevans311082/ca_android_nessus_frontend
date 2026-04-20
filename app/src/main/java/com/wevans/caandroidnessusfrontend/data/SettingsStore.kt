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
    private val accessKeyPref = stringPreferencesKey("access_key")
    private val secretKeyPref = stringPreferencesKey("secret_key")

    val settings: Flow<NessusSettings> = context.dataStore.data.map { prefs ->
        NessusSettings(
            baseUrl = prefs[baseUrlKey].orEmpty(),
            accessKey = prefs[accessKeyPref].orEmpty(),
            secretKey = prefs[secretKeyPref].orEmpty()
        )
    }

    suspend fun save(settings: NessusSettings) {
        context.dataStore.edit { prefs ->
            prefs[baseUrlKey] = settings.baseUrl.trim()
            prefs[accessKeyPref] = settings.accessKey.trim()
            prefs[secretKeyPref] = settings.secretKey.trim()
        }
    }
}
