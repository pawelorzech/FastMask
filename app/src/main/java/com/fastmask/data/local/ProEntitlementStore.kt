package com.fastmask.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fastmask.domain.model.ProStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The corruption handler is not optional here, and it is the counterpart of the
 * one [com.fastmask.data.local.settingsDataStore] already carries.
 *
 * A truncated `pro_entitlement.preferences_pb` (interrupted write, full disk)
 * makes every read *and every write* throw `CorruptionException`. Reads were
 * already guarded at the call site, so the app degraded to FREE and carried on
 * — but the write on the next Play reconciliation was not, and it runs inside a
 * bare `lifecycleScope.launch { proRepository.refresh() }`, where a throwable
 * reaches the uncaught handler and kills the process. That is a permanent
 * launch-time crash loop, and it can only happen to someone who actually paid:
 * a FREE user reconciles to the same status with the same null token, so no
 * write is attempted.
 *
 * Replacing the file costs the offline entitlement cache, which Play restores on
 * the very next reconciliation. Crashing costs the app.
 */
private val Context.proDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "pro_entitlement",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/**
 * Offline cache of the last Play-verified Pro entitlement status. Play remains
 * the authoritative source of truth and is consulted on every app start.
 *
 * The cache stores two fields: the entitlement [statusKey] (PRO or FREE) and a
 * SHA-256 digest of the purchase token (stored in the [proofKey] field as a
 * presence marker only, never for verification or HMAC). On a rooted device,
 * the DataStore file can be edited directly — there is no tamper resistance.
 * The proof field simply indicates whether the cached PRO status is backed by
 * a purchase token or is an orphaned cache entry.
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
