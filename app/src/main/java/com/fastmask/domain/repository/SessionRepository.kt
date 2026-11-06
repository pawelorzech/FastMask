package com.fastmask.domain.repository

import com.fastmask.domain.model.Session
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * The only way in and out of a session, real or demo.
 *
 * Everything that belongs to one session (the token, the JMAP session, the
 * offline snapshot, CSV exports, the demo list and its tutorial) is created and
 * dropped here, so a new kind of account data only has to be added in one place.
 */
interface SessionRepository {
    /** [Session.SIGNED_OUT] until [current] has read storage once. */
    val session: StateFlow<Session>

    /** The stored session, read on first call and kept in [session] after that. */
    suspend fun current(): Session

    suspend fun signIn(token: String): Result<Unit>

    /** Starts the demo from its seed list. Throws if the mode could not be stored. */
    suspend fun enterDemo()

    /** Ends any session, real or demo, and drops everything that belonged to it. */
    suspend fun signOut()

    val tutorialCompleted: Flow<Boolean>

    suspend fun completeTutorial()
}
