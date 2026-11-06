package com.fastmask.testutil

import com.fastmask.domain.model.CreateMaskedEmailParams
import com.fastmask.domain.model.EmailState
import com.fastmask.domain.model.CachedMasks
import com.fastmask.domain.model.MaskedEmail
import com.fastmask.domain.model.Session
import com.fastmask.domain.model.UpdateMaskedEmailParams
import com.fastmask.domain.repository.AppLockSetting
import com.fastmask.domain.repository.MaskedEmailRepository
import com.fastmask.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    /**
     * Overrides what [getMaskedEmail] answers; null derives from [emails].
     * Lets a test distinguish "server does not know this id" (`success(null)`)
     * from a transport failure.
     */
    var singleResult: Result<MaskedEmail?>? = null,
) : MaskedEmailRepository {

    override suspend fun cachedMaskedEmails(): CachedMasks? = cached

    var getCalls = 0
    var createCalls = 0
    var updateCalls = 0
    var archiveCalls = 0
    var destroyCalls = 0
    var getSingleCalls = 0
    var lastArchiveId: String? = null
    var lastDestroyId: String? = null
    var lastUpdateId: String? = null
    var lastUpdateParams: UpdateMaskedEmailParams? = null
    var lastCreateParams: CreateMaskedEmailParams? = null
    var lastGetSingleId: String? = null

    /**
     * Runs before the single-mask fetch answers, so a test can hold the fresh
     * response back and assert against what the cache seed rendered first.
     */
    var beforeGetSingle: (suspend () -> Unit)? = null

    /**
     * Runs before the fetch answers, so a test can interleave something with an
     * in-flight request — e.g. a sign-out landing before the response does.
     */
    var beforeGet: (suspend () -> Unit)? = null
    var beforeUpdate: (suspend () -> Unit)? = null

    override suspend fun getMaskedEmails(): Result<List<MaskedEmail>> {
        getCalls++
        beforeGet?.invoke()
        failure?.let { return Result.failure(it) }
        return Result.success(emails)
    }

    override suspend fun getMaskedEmail(id: String): Result<MaskedEmail?> {
        getSingleCalls++
        lastGetSingleId = id
        beforeGetSingle?.invoke()
        singleResult?.let { return it }
        failure?.let { return Result.failure(it) }
        return Result.success(emails.find { it.id == id })
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
        beforeUpdate?.invoke()
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

class FakeSessionRepository(
    initial: Session = Session.SIGNED_OUT,
    var signInResult: Result<Unit> = Result.success(Unit),
    /** Thrown by [enterDemo], to simulate a failed DataStore write. */
    var enterDemoFailure: Throwable? = null,
) : SessionRepository {
    private val _session = MutableStateFlow(initial)
    override val session: StateFlow<Session> = _session
    val tutorial = MutableStateFlow(false)
    override val tutorialCompleted: Flow<Boolean> = tutorial

    var signInCalls = 0
    var lastToken: String? = null
    var enterDemoCalls = 0
    var signOutCalls = 0

    override suspend fun current(): Session = _session.value

    override suspend fun signIn(token: String): Result<Unit> {
        signInCalls++
        lastToken = token
        return signInResult.onSuccess { _session.value = Session.ACCOUNT }
    }

    override suspend fun enterDemo() {
        enterDemoCalls++
        enterDemoFailure?.let { throw it }
        _session.value = Session.DEMO
    }

    override suspend fun signOut() {
        signOutCalls++
        _session.value = Session.SIGNED_OUT
    }

    override suspend fun completeTutorial() {
        tutorial.value = true
    }
}

class FakeAppLockSetting(var enabled: Boolean = false) : AppLockSetting {
    override suspend fun isAppLockEnabled(): Boolean = enabled
}
