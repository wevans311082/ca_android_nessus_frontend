package com.wevans.caandroidnessusfrontend.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.onStart

data class NessusSettings(
    val baseUrl: String = "",
    val accessKey: String = "",
    val secretKey: String = ""
)

class SettingsStore(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPrefs = EncryptedSharedPreferences.create(
        context,
        "nessus_secure_settings",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val _settingsFlow = MutableStateFlow(readSettings())
    val settings: Flow<NessusSettings> = _settingsFlow

    private fun readSettings(): NessusSettings {
        return NessusSettings(
            baseUrl = sharedPrefs.getString("base_url", "").orEmpty(),
            accessKey = sharedPrefs.getString("access_key", "").orEmpty(),
            secretKey = sharedPrefs.getString("secret_key", "").orEmpty()
        )
    }

    fun save(settings: NessusSettings) {
        sharedPrefs.edit().apply {
            putString("base_url", settings.baseUrl.trim())
            putString("access_key", settings.accessKey.trim())
            putString("secret_key", settings.secretKey.trim())
        }.apply()
        _settingsFlow.value = readSettings()
    }
}
