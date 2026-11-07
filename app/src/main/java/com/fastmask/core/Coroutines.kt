package com.fastmask.core

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlin.coroutines.cancellation.CancellationException

/**
 * Launch context for a write to DataStore or the token store (completeTutorial,
 * setAccent, setAppLockEnabled, signOut, crash-reporting opt-out, language).
 *
 * Such a write can throw (disk full, KeyStore corruption), and `viewModelScope`
 * rethrows an uncaught exception on the main thread, which crashes the app. This
 * swallows it so a failed write degrades quietly. When it aborts the coroutine
 * before a post-write step (a navigation event, an `onDone` callback), that step
 * simply does not run: the user stays put rather than moving on a half-applied
 * change. Pass it to every `launch` that persists a user setting.
 */
val QuietWriteFailure: CoroutineExceptionHandler = CoroutineExceptionHandler { _, _ -> }

/**
 * [runCatching] for suspend code that keeps structured cancellation intact.
 *
 * Kotlin's standard `runCatching` also catches [CancellationException], turning
 * a closed screen or a timeout into an ordinary failure and letting the
 * cancelled coroutine carry on mutating stale state. This variant rethrows it
 * and wraps every other [Throwable] in a failed [Result].
 *
 * Use plain `runCatching` only where the block cannot suspend.
 */
suspend inline fun <T> runSuspendCatching(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Throwable) {
        Result.failure(error)
    }
