package com.fastmask.domain.repository

interface AuthRepository {
    suspend fun login(token: String): Result<Unit>
    fun logout()
    fun isLoggedIn(): Boolean
    fun getToken(): String?
}
