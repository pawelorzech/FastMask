package com.fastmask.domain.repository

interface AuthRepository {
    suspend fun login(token: String): Result<Unit>
    suspend fun logout()
    fun isLoggedIn(): Boolean
    fun getToken(): String?
}
