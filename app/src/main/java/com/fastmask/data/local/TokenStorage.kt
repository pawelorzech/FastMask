package com.fastmask.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val sharedPreferences: SharedPreferences by lazy {
        try {
            createEncryptedPrefs()
        } catch (e: Exception) {
            // The Tink keyset or the KeyStore master key can become unreadable
            // (OS update, keystore corruption, restored data without the key).
            // Without recovery the app would crash-loop on every launch.
            // Deleting the pref file (and retrying once) recovers the app at the
            // cost of a forced re-login — the token is re-obtainable by the user.
            context.deleteSharedPreferences(PREFS_FILE_NAME)
            createEncryptedPrefs()
        }
    }

    private fun createEncryptedPrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveToken(token: String) {
        sharedPreferences.edit().putString(KEY_API_TOKEN, token).apply()
    }

    fun getToken(): String? {
        return try {
            sharedPreferences.getString(KEY_API_TOKEN, null)
        } catch (e: Exception) {
            // Covers both a single-value decryption failure (SecurityException)
            // and a double-fault where the lazy initializer's retry also failed
            // (KeyStore unavailable). Treat as logged out instead of crashing —
            // saveToken() will surface the underlying error at login time.
            null
        }
    }

    fun clearToken() {
        sharedPreferences.edit().remove(KEY_API_TOKEN).apply()
    }

    fun hasToken(): Boolean {
        return getToken() != null
    }

    companion object {
        private const val PREFS_FILE_NAME = "fastmask_secure_prefs"
        private const val KEY_API_TOKEN = "api_token"
    }
}
