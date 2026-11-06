package com.fastmask.data.session

import com.fastmask.core.IoDispatcher
import com.fastmask.core.runSuspendCatching
import com.fastmask.data.api.JmapApi
import com.fastmask.data.local.ExportCache
import com.fastmask.data.local.MaskedEmailCache
import com.fastmask.data.local.TokenStorage
import com.fastmask.data.repository.DemoMaskedEmailRepositoryImpl
import com.fastmask.domain.model.Session
import com.fastmask.domain.repository.SessionRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepositoryImpl @Inject constructor(
    private val tokenStorage: TokenStorage,
    private val jmapApi: JmapApi,
    private val store: SessionStore,
    private val demoRepository: DemoMaskedEmailRepositoryImpl,
    private val exportCache: ExportCache,
    private val maskCache: MaskedEmailCache,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : SessionRepository {

    private val mutex = Mutex()
    private var loaded = false
    private val _session = MutableStateFlow(Session.SIGNED_OUT)
    override val session: StateFlow<Session> = _session.asStateFlow()

    override val tutorialCompleted: Flow<Boolean> = store.tutorialCompleted

    override suspend fun current(): Session = mutex.withLock {
        if (!loaded) publish(withContext(ioDispatcher) { read() })
        _session.value
    }

    private suspend fun read(): Session = when {
        // Demo first: it needs no token, and asking for one opens
        // EncryptedSharedPreferences (Tink + Keystore), the slowest read at
        // startup. An unreadable preferences file falls back to the real
        // account rather than silently into the demo.
        runSuspendCatching { store.isDemo() }.getOrDefault(false) -> Session.DEMO
        tokenStorage.hasToken() -> Session.ACCOUNT
        else -> Session.SIGNED_OUT
    }

    override suspend fun signIn(token: String): Result<Unit> = withContext(ioDispatcher) {
        // Cleared before the new account starts, not only on sign-out: a
        // sign-out that never ran (an unreadable token store drops the user on
        // Welcome without one) must not hand the previous account's data on.
        clearAccountData()
        jmapApi.getSession(token).map {
            mutex.withLock {
                tokenStorage.saveToken(token)
                publish(Session.ACCOUNT)
            }
        }
    }

    override suspend fun enterDemo() = mutex.withLock {
        // The demo repository lives for the whole process, so without the
        // reset a second demo would reopen the first one's edits.
        demoRepository.reset()
        store.startDemo()
        publish(Session.DEMO)
    }

    override suspend fun signOut() = withContext(ioDispatcher) {
        mutex.withLock {
            tokenStorage.clearToken()
            clearAccountData()
            store.endDemo()
            publish(Session.SIGNED_OUT)
        }
    }

    override suspend fun completeTutorial() = store.completeTutorial()

    /**
     * Everything derived from one account. The offline snapshot and CSV exports
     * hold every mask in plaintext and must not outlive the account they came from.
     */
    private fun clearAccountData() {
        jmapApi.clearSession()
        exportCache.clear()
        maskCache.clear()
    }

    private fun publish(session: Session) {
        loaded = true
        _session.value = session
    }
}
