package com.fastmask.testutil

import com.fastmask.domain.model.AppMode
import com.fastmask.domain.model.CreateMaskedEmailParams
import com.fastmask.domain.model.EmailState
import com.fastmask.domain.model.CachedMasks
import com.fastmask.domain.model.MaskedEmail
import com.fastmask.domain.model.UpdateMaskedEmailParams
import com.fastmask.domain.repository.AuthRepository
import com.fastmask.domain.repository.MaskedEmailRepository
import com.fastmask.domain.repository.QuickMaskGuard
import com.fastmask.domain.usecase.DemoModeActivator
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
    /** Snapshot an offline read returns; null means "nothing cached". */
    var cached: CachedMasks? = null,
) : MaskedEmailRepository {

    override suspend fun cachedMaskedEmails(): CachedMasks? = cached

    var getCalls = 0
    var createCalls = 0
    var updateCalls = 0
    var archiveCalls = 0
    var destroyCalls = 0
    var lastArchiveId: String? = null
    var lastDestroyId: String? = null
    var lastUpdateId: String? = null
    var lastUpdateParams: UpdateMaskedEmailParams? = null
    var lastCreateParams: CreateMaskedEmailParams? = null

    /**
     * Runs before the fetch answers, so a test can interleave something with an
     * in-flight request — e.g. a sign-out landing before the response does.
     */
    var beforeGet: (suspend () -> Unit)? = null

    override suspend fun getMaskedEmails(): Result<List<MaskedEmail>> {
        getCalls++
        beforeGet?.invoke()
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

    override suspend fun archiveMaskedEmail(id: String): Result<Unit> {
        archiveCalls++
        lastArchiveId = id
        failure?.let { return Result.failure(it) }
        return Result.success(Unit)
    }

    override suspend fun destroyMaskedEmail(id: String): Result<Unit> {
        destroyCalls++
        lastDestroyId = id
        failure?.let { return Result.failure(it) }
        return Result.success(Unit)
    }
}

class FakeAuthRepository(
    var loginResult: Result<Unit> = Result.success(Unit),
    /**
     * What [isLoggedIn] answers. Mirrors the real implementation's contract:
     * a DEMO session also reports `true` (see AuthRepositoryImpl), so callers
     * must not treat "logged in" as "has a real Fastmail token".
     */
    var loggedIn: Boolean = false,
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

    override fun isLoggedIn(): Boolean = loggedIn

    override fun getToken(): String? = null
}

/**
 * The shared way into demo mode. Counting calls is how the tests assert that
 * Welcome and Login run the *same* mechanism rather than two copies of it.
 */
class FakeDemoModeActivator(
    /** Set to simulate a failed DataStore write. */
    var failure: Throwable? = null,
) : DemoModeActivator {
    var activateCalls = 0

    override suspend fun activate() {
        activateCalls++
        failure?.let { throw it }
    }
}

/** Preconditions for the quick-create entry points (tile / launcher shortcut). */
class FakeQuickMaskGuard(
    var mode: AppMode = AppMode.REAL,
    var lockEnabled: Boolean = false,
) : QuickMaskGuard {
    override suspend fun appMode(): AppMode = mode
    override suspend fun appLockEnabled(): Boolean = lockEnabled
}
