package com.fastmask.data.repository

import com.fastmask.data.api.JmapApi
import com.fastmask.data.local.ExportCache
import com.fastmask.data.local.MaskedEmailCache
import com.fastmask.data.local.SettingsDataStore
import com.fastmask.data.local.TokenStorage
import com.fastmask.domain.model.AppMode
import com.fastmask.domain.repository.AuthRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val tokenStorage: TokenStorage,
    private val jmapApi: JmapApi,
    private val settingsDataStore: SettingsDataStore,
    private val exportCache: ExportCache,
    private val maskCache: MaskedEmailCache,
) : AuthRepository {

    override suspend fun login(token: String): Result<Unit> {
        return jmapApi.getSession(token).map {
            // Sign-in is the second place the device changes hands between
            // accounts, and until now only sign-out cleaned up. A sign-out
            // whose clear() silently failed, or a session that ended without
            // one (an unreadable token keyset makes isLoggedIn() answer false
            // and drops the user back on the welcome screen), left the previous
            // account's snapshot and exports in place for the next person to
            // sign in. Clearing here makes the guarantee independent of
            // sign-out having worked.
            maskCache.clear()
            exportCache.clear()
            tokenStorage.saveToken(token)
        }
    }

    override suspend fun logout() {
        tokenStorage.clearToken()
        jmapApi.clearSession()
        // A CSV export holds every mask in plaintext in the cache directory.
        // Ageing it out after an hour is right while signed in, but signing out
        // is exactly when the account's data should stop being on the device —
        // so drop it now rather than let it outlive the session.
        exportCache.clear()
        // The offline snapshot is the whole mask list at rest; it must not
        // survive the account it belongs to.
        maskCache.clear()
        // Clear demo flag and tutorial state so the next session starts fresh.
        settingsDataStore.setAppMode(AppMode.REAL)
        settingsDataStore.setTutorialCompleted(false)
    }

    override fun isLoggedIn(): Boolean {
        return tokenStorage.hasToken() || settingsDataStore.appModeBlocking() == AppMode.DEMO
    }

    override fun getToken(): String? {
        return tokenStorage.getToken()
    }
}
