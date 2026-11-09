package com.fastmask.data.session

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.fastmask.data.local.settingsDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** The keys and stored values predate this class and must not change, or upgrades lose the demo state. */
@Singleton
class SessionStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun isDemo(): Boolean = context.settingsDataStore.data.first()[APP_MODE] == DEMO

    val tutorialCompleted: Flow<Boolean> =
        context.settingsDataStore.data.map { it[TUTORIAL_COMPLETED] ?: false }

    suspend fun startDemo() = setMode(DEMO)

    suspend fun endDemo() = setMode(REAL)

    private suspend fun setMode(mode: String) {
        context.settingsDataStore.edit {
            it[APP_MODE] = mode
            it[TUTORIAL_COMPLETED] = false
        }
    }

    suspend fun completeTutorial() {
        context.settingsDataStore.edit { it[TUTORIAL_COMPLETED] = true }
    }

    private companion object {
        val APP_MODE = stringPreferencesKey("app_mode")
        val TUTORIAL_COMPLETED = booleanPreferencesKey("tutorial_completed")
        const val DEMO = "DEMO"
        const val REAL = "REAL"
    }
}
