package com.fastmask.core

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlin.coroutines.cancellation.CancellationException

/**
 * A write to DataStore or the token store can throw (disk full, KeyStore
 * corruption), and `viewModelScope` rethrows an uncaught exception on the main
 * thread, which crashes the app.
 */
val QuietWriteFailure: CoroutineExceptionHandler = CoroutineExceptionHandler { _, _ -> }

/**
 * Kotlin's standard `runCatching` also catches [CancellationException], turning
 * a closed screen or a timeout into an ordinary failure and letting the
 * cancelled coroutine carry on mutating stale state.
 */
suspend inline fun <T> runSuspendCatching(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Throwable) {
        Result.failure(error)
    }
