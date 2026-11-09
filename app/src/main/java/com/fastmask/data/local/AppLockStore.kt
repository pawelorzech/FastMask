package com.fastmask.data.local

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.preferencesDataStore
import com.fastmask.core.runSuspendCatching
import com.fastmask.domain.repository.AppLockSetting
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val APP_LOCK_KEY = booleanPreferencesKey("app_lock_enabled")

private const val RETRY_BASE_MS = 500L
private const val RETRY_MAX_MS = 30_000L

/** What a corrupt file becomes: an armed lock. An empty file would read as "never armed". */
internal val LOCKED_ON_CORRUPTION: ReplaceFileCorruptionHandler<Preferences> =
    ReplaceFileCorruptionHandler { preferencesOf(APP_LOCK_KEY to true) }

val Context.appLockDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "app_lock",
    corruptionHandler = LOCKED_ON_CORRUPTION,
    produceMigrations = { context -> listOf(AppLockMigration(context.settingsDataStore)) },
)

/**
 * The app lock flag in a file of its own: it is the one setting whose safe default
 * after a storage problem is ON, the opposite of every key in the general settings
 * file. Fails closed here and nowhere else: a corrupt file and a read error both mean locked.
 */
@Singleton
class AppLockStore(
    private val dataStore: DataStore<Preferences>,
) : AppLockSetting {

    @Inject
    constructor(@ApplicationContext context: Context) : this(context.appLockDataStore)

    /**
     * A missing key in a healthy file is disabled: a user who never armed the lock must
     * not be locked. A read error reports locked and then reads again, so a collector
     * that outlives the error still sees the user turn the lock off.
     */
    val appLockEnabled: Flow<Boolean> = dataStore.data
        .map { preferences -> preferences[APP_LOCK_KEY] ?: false }
        .retryWhen { cause, attempt ->
            if (cause !is IOException) return@retryWhen false
            emit(true)
            delay((RETRY_BASE_MS shl attempt.coerceAtMost(6).toInt()).coerceAtMost(RETRY_MAX_MS))
            true
        }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[APP_LOCK_KEY] = enabled }
    }

    override suspend fun isAppLockEnabled(): Boolean = appLockEnabled.first()

    /** The startup decision: a lock value that cannot be determined starts the gate locked. */
    suspend fun lockAtLaunch(): Boolean = runSuspendCatching { appLockEnabled.first() }.getOrElse { true }
}

/**
 * A lock armed before the update stays armed after it. The old key is removed in
 * [cleanUp], which DataStore runs only after the migrated data has been persisted.
 */
internal class AppLockMigration(
    private val oldStore: DataStore<Preferences>,
) : DataMigration<Preferences> {

    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        APP_LOCK_KEY !in currentData && oldValue() != null

    override suspend fun migrate(currentData: Preferences): Preferences {
        val value = oldValue() ?: return currentData
        return currentData.toMutablePreferences().apply { this[APP_LOCK_KEY] = value }
    }

    override suspend fun cleanUp() {
        oldStore.edit { preferences: MutablePreferences -> preferences.remove(APP_LOCK_KEY) }
    }

    private suspend fun oldValue(): Boolean? =
        oldStore.data.first().asMap()[APP_LOCK_KEY] as? Boolean
}
