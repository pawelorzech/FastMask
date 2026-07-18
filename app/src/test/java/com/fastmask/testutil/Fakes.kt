package com.fastmask.testutil

import com.fastmask.domain.model.CreateMaskedEmailParams
import com.fastmask.domain.model.EmailState
import com.fastmask.domain.model.MaskedEmail
import com.fastmask.domain.model.UpdateMaskedEmailParams
import com.fastmask.domain.repository.AuthRepository
import com.fastmask.domain.repository.MaskedEmailRepository
import java.time.Instant

fun mask(
    id: String,
    state: EmailState = EmailState.ENABLED,
    description: String? = null,
    forDomain: String? = null,
    url: String? = null,
    createdAt: Instant? = null,
    lastMessageAt: Instant? = null,
) = MaskedEmail(
    id = id,
    email = "$id@fastmail.com",
    state = state,
    forDomain = forDomain,
    description = description,
    createdBy = null,
    url = url,
    emailPrefix = null,
    createdAt = createdAt,
    lastMessageAt = lastMessageAt,
)

class FakeMaskedEmailRepository(
    var emails: List<MaskedEmail> = emptyList(),
    var failure: Throwable? = null,
) : MaskedEmailRepository {

    var getCalls = 0
    var createCalls = 0
    var updateCalls = 0
    var deleteCalls = 0
    var lastUpdateId: String? = null
    var lastUpdateParams: UpdateMaskedEmailParams? = null
    var lastCreateParams: CreateMaskedEmailParams? = null

    override suspend fun getMaskedEmails(): Result<List<MaskedEmail>> {
        getCalls++
        failure?.let { return Result.failure(it) }
        return Result.success(emails)
    }

    override suspend fun createMaskedEmail(params: CreateMaskedEmailParams): Result<MaskedEmail> {
        createCalls++
        lastCreateParams = params
        failure?.let { return Result.failure(it) }
        return Result.success(mask("created"))
    }

    override suspend fun updateMaskedEmail(id: String, params: UpdateMaskedEmailParams): Result<Unit> {
        updateCalls++
        lastUpdateId = id
        lastUpdateParams = params
        failure?.let { return Result.failure(it) }
        return Result.success(Unit)
    }

    override suspend fun deleteMaskedEmail(id: String): Result<Unit> {
        deleteCalls++
        failure?.let { return Result.failure(it) }
        return Result.success(Unit)
    }
}

class FakeAuthRepository(
    var loginResult: Result<Unit> = Result.success(Unit),
) : AuthRepository {
    var loginCalls = 0
    var lastToken: String? = null
    var loggedOut = false

    override suspend fun login(token: String): Result<Unit> {
        loginCalls++
        lastToken = token
        return loginResult
    }

    override suspend fun logout() {
        loggedOut = true
    }

    override fun isLoggedIn(): Boolean = false

    override fun getToken(): String? = null
}
