package com.fastmask.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Runs at most one load at a time and coalesces everything that arrives meanwhile.
 *
 * A request that comes in while a run is in flight is not dropped (the newer
 * answer would be lost) and does not start a concurrent run (two results would
 * race, last writer wins). It is queued, and exactly one more run starts after
 * the current one ends, however many requests piled up. Queued arguments are
 * folded together with [merge]. The re-run is skipped once [scope] is cancelled.
 *
 * [start] is called synchronously on the caller's thread, before the coroutine
 * is dispatched, so it can flip loading state and two calls in the same frame
 * cannot both start. It returns the suspending body of that run.
 */
class SingleFlight<A>(
    private val scope: CoroutineScope,
    private val merge: (queued: A, new: A) -> A,
    private val start: (A) -> suspend () -> Unit,
) {
    private var inFlight = false
    private var hasQueued = false
    private var queued: A? = null

    fun request(arg: A) {
        if (inFlight) {
            @Suppress("UNCHECKED_CAST")
            queued = if (hasQueued) merge(queued as A, arg) else arg
            hasQueued = true
            return
        }
        inFlight = true
        val body = start(arg)
        scope.launch {
            try {
                body()
            } finally {
                inFlight = false
                if (hasQueued) {
                    @Suppress("UNCHECKED_CAST")
                    val next = queued as A
                    hasQueued = false
                    queued = null
                    if (isActive) request(next)
                }
            }
        }
    }
}
