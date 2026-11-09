package com.fastmask.domain.repository

import com.fastmask.domain.model.Session
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface SessionRepository {
    /** [Session.SIGNED_OUT] until [current] has read storage once. */
    val session: StateFlow<Session>

    suspend fun current(): Session

    suspend fun signIn(token: String): Result<Unit>

    /** Starts the demo from its seed list. Throws if the mode could not be stored. */
    suspend fun enterDemo()

    suspend fun signOut()

    val tutorialCompleted: Flow<Boolean>

    suspend fun completeTutorial()
}
