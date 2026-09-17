package com.kmarko.nasdrive

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Persists the last-used NAS connection (including password) in an encrypted prefs file. */
class ConfigStore(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "nas_config",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun save(config: SmbConfig) {
        prefs.edit()
            .putString("host", config.host)
            .putInt("port", config.port)
            .putString("share", config.shareName)
            .putString("username", config.username)
            .putString("password", config.password)
            .putString("domain", config.domain)
            .apply()
    }

    fun load(): SmbConfig? {
        val host = prefs.getString("host", null) ?: return null
        val share = prefs.getString("share", null) ?: return null
        return SmbConfig(
            host = host,
            port = prefs.getInt("port", 445),
            shareName = share,
            username = prefs.getString("username", "") ?: "",
            password = prefs.getString("password", "") ?: "",
            domain = prefs.getString("domain", "") ?: ""
        )
    }
}
