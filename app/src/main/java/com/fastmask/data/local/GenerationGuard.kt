package com.fastmask.data.local

import java.util.concurrent.atomic.AtomicLong

/**
 * Sign-out guard: a fetch that outlives a sign-out must not write the account's
 * data back to disk (`docs/privacy.md` promises sign-out removes it).
 *
 *  1. before the slow work, take [current];
 *  2. after it, inside the store's own lock, write only if [isCurrent];
 *  3. the store's `clear()` calls [advance], inside the same lock.
 */
internal class GenerationGuard {
    private val generation = AtomicLong(0)

    fun current(): Long = generation.get()

    fun isCurrent(snapshot: Long): Boolean = snapshot == generation.get()

    fun advance() {
        generation.incrementAndGet()
    }
}
