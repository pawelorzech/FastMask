package com.fastmask.core

import kotlin.coroutines.cancellation.CancellationException

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
