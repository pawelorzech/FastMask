package com.fastmask.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fastmask.core.sha256Hex
import com.fastmask.domain.model.ProStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A truncated `pro_entitlement.preferences_pb` (interrupted write, full disk) makes
 * every read *and every write* throw `CorruptionException`: a permanent launch-time
 * crash loop for anyone who paid. Replacing the file only costs the offline cache,
 * which Play restores on the next reconciliation.
 */
private val Context.proDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "pro_entitlement",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/**
 * The SHA-256 digest of the purchase token in [proofKey] is a presence marker
 * only, never used for verification. On a rooted device the DataStore file can be
 * edited directly: there is no tamper resistance.
 */
@Singleton
class ProEntitlementStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val statusKey = stringPreferencesKey("status")
    private val proofKey = stringPreferencesKey("proof")

    suspend fun read(): ProStatus {
        val preferences = context.proDataStore.data.first()
        val status = preferences[statusKey]?.let { value ->
            runCatching { ProStatus.valueOf(value) }.getOrNull()
        } ?: ProStatus.FREE
        return if (status == ProStatus.PRO && preferences[proofKey].isNullOrEmpty()) {
            ProStatus.FREE
        } else {
            status
        }
    }

    suspend fun write(status: ProStatus, purchaseToken: String?) {
        context.proDataStore.edit { preferences ->
            preferences[statusKey] = status.name
            if (status == ProStatus.PRO && purchaseToken != null) {
                preferences[proofKey] = purchaseToken.sha256Hex()
            } else {
                preferences.remove(proofKey)
            }
        }
    }
}
