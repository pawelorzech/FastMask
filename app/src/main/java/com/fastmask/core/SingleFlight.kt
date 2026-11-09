package com.fastmask.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Runs at most one load at a time and coalesces everything that arrives meanwhile. */
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
