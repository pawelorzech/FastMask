package com.fastmask.data.repository

import com.fastmask.data.api.JmapApi
import com.fastmask.data.local.TokenStorage
import com.fastmask.domain.repository.AuthRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val tokenStorage: TokenStorage,
    private val jmapApi: JmapApi
) : AuthRepository {

    override suspend fun login(token: String): Result<Unit> {
        return jmapApi.getSession(token).map {
            tokenStorage.saveToken(token)
        }
    }

    override fun logout() {
        tokenStorage.clearToken()
        jmapApi.clearSession()
    }

    override fun isLoggedIn(): Boolean {
        return tokenStorage.hasToken()
    }

    override fun getToken(): String? {
        return tokenStorage.getToken()
    }
}
