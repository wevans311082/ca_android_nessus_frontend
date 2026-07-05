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
    val secretKey: String = "",
    val scannerId: String = "1",  // Default for many on-prem; "null" for Tenable.io cloud
    val pollingIntervalMs: Long = 2000,
    val exportTimeoutSeconds: Int = 300,
    val requireBiometric: Boolean = false
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
            secretKey = sharedPrefs.getString("secret_key", "").orEmpty(),
            scannerId = sharedPrefs.getString("scanner_id", "1").orEmpty().ifBlank { "1" },
            pollingIntervalMs = sharedPrefs.getLong("polling_interval_ms", 2000),
            exportTimeoutSeconds = sharedPrefs.getInt("export_timeout_sec", 300),
            requireBiometric = sharedPrefs.getBoolean("require_biometric", false)
        )
    }

    fun save(settings: NessusSettings) {
        sharedPrefs.edit().apply {
            putString("base_url", settings.baseUrl.trim())
            putString("access_key", settings.accessKey.trim())
            putString("secret_key", settings.secretKey.trim())
            putString("scanner_id", settings.scannerId.trim().ifBlank { "1" })
            putLong("polling_interval_ms", settings.pollingIntervalMs)
            putInt("export_timeout_sec", settings.exportTimeoutSeconds)
            putBoolean("require_biometric", settings.requireBiometric)
        }.apply()
        _settingsFlow.value = readSettings()
    }
}
