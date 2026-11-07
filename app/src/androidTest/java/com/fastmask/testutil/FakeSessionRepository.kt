package com.fastmask.testutil

import com.fastmask.domain.model.Session
import com.fastmask.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf

/**
 * A session store that never touches storage, for building a real view model
 * (e.g. `LoginViewModel`) around a screen without the Hilt graph.
 */
class FakeSessionRepository : SessionRepository {
    override val session: StateFlow<Session> = MutableStateFlow(Session.SIGNED_OUT)
    override suspend fun current(): Session = session.value
    override suspend fun signIn(token: String): Result<Unit> = Result.success(Unit)
    override suspend fun enterDemo() = Unit
    override suspend fun signOut() = Unit
    override val tutorialCompleted: Flow<Boolean> = flowOf(false)
    override suspend fun completeTutorial() = Unit
}
