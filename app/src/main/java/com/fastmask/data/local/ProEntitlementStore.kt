package com.fastmask.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fastmask.domain.model.ProStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

private val Context.proDataStore: DataStore<Preferences> by preferencesDataStore(name = "pro_entitlement")

/**
 * Local cache of the last Play-verified entitlement, so Pro features work
 * offline. Not a plain user-editable boolean: the status is stored together
 * with a SHA-256 digest of the purchase token that granted it (never the token
 * itself), and it is reconciled against Play on every app start — Play stays
 * the source of truth.
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
        // A PRO status without its proof digest is not a state this app writes —
        // treat as FREE until the next Play reconciliation confirms it.
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
                preferences[proofKey] = purchaseToken.sha256()
            } else {
                preferences.remove(proofKey)
            }
        }
    }

    private fun String.sha256(): String =
        MessageDigest.getInstance("SHA-256")
            .digest(toByteArray())
            .joinToString("") { "%02x".format(it) }
}
