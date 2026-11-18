package com.fastmask.data.review

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Its own file, so a corrupt settings store cannot reset the "already asked" latch.
 * A corrupt review file resets to "never asked", which at worst costs one extra ask.
 */
private val Context.reviewDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "review_prefs",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/** The two facts the prompt needs: masks created, and whether Play was already asked. */
@Singleton
class ReviewPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val store get() = context.reviewDataStore

    suspend fun recordMaskCreated(): Int {
        var result = 0
        store.edit { prefs ->
            // Saturating, so an absurd count cannot wrap negative.
            val next = (prefs[CREATED] ?: 0).let { if (it == Int.MAX_VALUE) it else it + 1 }
            prefs[CREATED] = next
            result = next
        }
        return result
    }

    suspend fun alreadyAsked(): Boolean = store.data.first()[ASKED] ?: false

    suspend fun markAsked() {
        store.edit { it[ASKED] = true }
    }

    private companion object {
        val CREATED = intPreferencesKey("masks_created")
        val ASKED = booleanPreferencesKey("review_asked")
    }
}
