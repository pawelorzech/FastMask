package com.fastmask.data.repository

import com.fastmask.data.api.JmapApi
import com.fastmask.data.local.SettingsDataStore
import com.fastmask.data.local.TokenStorage
import com.fastmask.domain.model.AppMode
import com.fastmask.domain.repository.AuthRepository
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val tokenStorage: TokenStorage,
    private val jmapApi: JmapApi,
    private val settingsDataStore: SettingsDataStore
) : AuthRepository {

    override suspend fun login(token: String): Result<Unit> {
        return jmapApi.getSession(token).map {
            tokenStorage.saveToken(token)
        }
    }

    override fun logout() {
        tokenStorage.clearToken()
        jmapApi.clearSession()
        // Clear demo flag and tutorial state so the next session starts fresh.
        runBlocking {
            settingsDataStore.setAppMode(AppMode.REAL)
            settingsDataStore.setTutorialCompleted(false)
        }
    }

    override fun isLoggedIn(): Boolean {
        return tokenStorage.hasToken() || settingsDataStore.appModeBlocking() == AppMode.DEMO
    }

    override fun getToken(): String? {
        return tokenStorage.getToken()
    }
}
