package com.wevans.caandroidnessusfrontend.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.security.MessageDigest
import java.security.SecureRandom

data class NessusSettings(
    val baseUrl: String = "",
    val accessKey: String = "",
    val secretKey: String = "",
    val scannerId: String = "1",  // Default for many on-prem; "null" for Tenable.io cloud
    val pollingIntervalMs: Long = 2000,
    val exportTimeoutSeconds: Int = 300,
    /** When true, the app requires unlock (biometric if available, otherwise/in addition app PIN). */
    val requireBiometric: Boolean = false,
    /** Whether an in-app backup PIN has been configured (hash stored; PIN itself is never stored). */
    val hasAppPin: Boolean = false
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

    /** Synchronous snapshot for ViewModel initial state (avoids a blank first frame). */
    val current: NessusSettings get() = _settingsFlow.value

    private fun readSettings(): NessusSettings {
        return NessusSettings(
            baseUrl = sharedPrefs.getString("base_url", "").orEmpty(),
            accessKey = sharedPrefs.getString("access_key", "").orEmpty(),
            secretKey = sharedPrefs.getString("secret_key", "").orEmpty(),
            scannerId = sharedPrefs.getString("scanner_id", "1").orEmpty().ifBlank { "1" },
            pollingIntervalMs = sharedPrefs.getLong("polling_interval_ms", 2000),
            exportTimeoutSeconds = sharedPrefs.getInt("export_timeout_sec", 300),
            requireBiometric = sharedPrefs.getBoolean("require_biometric", false),
            hasAppPin = sharedPrefs.getString("app_pin_hash", "").orEmpty().isNotBlank()
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
            // PIN hash/salt are managed only via setAppPin / clearAppPin
        }.apply()
        _settingsFlow.value = readSettings()
    }

    fun hasAppPin(): Boolean =
        sharedPrefs.getString("app_pin_hash", "").orEmpty().isNotBlank()

    /**
     * Stores a salted SHA-256 hash of [pin]. PIN must be 4–8 digits.
     * @return null on success, or an error message
     */
    fun setAppPin(pin: String): String? {
        val validation = validatePinFormat(pin)
        if (validation != null) return validation

        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            .joinToString("") { "%02x".format(it) }
        val hash = hashPin(pin, salt)

        sharedPrefs.edit()
            .putString("app_pin_salt", salt)
            .putString("app_pin_hash", hash)
            .apply()
        _settingsFlow.value = readSettings()
        return null
    }

    fun verifyAppPin(pin: String): Boolean {
        val salt = sharedPrefs.getString("app_pin_salt", "").orEmpty()
        val expected = sharedPrefs.getString("app_pin_hash", "").orEmpty()
        if (salt.isBlank() || expected.isBlank()) return false
        return hashPin(pin, salt) == expected
    }

    fun clearAppPin() {
        sharedPrefs.edit()
            .remove("app_pin_salt")
            .remove("app_pin_hash")
            .apply()
        _settingsFlow.value = readSettings()
    }

    companion object {
        fun validatePinFormat(pin: String): String? {
            if (!pin.matches(Regex("^\\d{4,8}$"))) {
                return "PIN must be 4–8 digits"
            }
            return null
        }

        fun hashPin(pin: String, salt: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val bytes = digest.digest("$salt:$pin".toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
