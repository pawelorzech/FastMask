package com.fastmask.data.local

import java.util.concurrent.atomic.AtomicLong

/**
 * The sign-out guard shared by the stores that hold an account's data
 * ([MaskedEmailCache], [ExportCache]), so the protocol exists once.
 *
 * Both are filled from a slow network fetch. The sequence "start a fetch, sign
 * out before it lands" would otherwise write the account's data back after the
 * sign-out that was supposed to erase it, and a lock alone cannot prevent that:
 * it orders the two file operations but says nothing about which the user asked
 * for first. Cancellation cannot either, since the write follows the fetch with
 * no suspension point in between.
 *
 * The protocol:
 *  1. before the slow work, take [current];
 *  2. after it, inside the store's own lock, write only if [isCurrent];
 *  3. the store's `clear()` calls [advance], inside the same lock.
 *
 * A store runs the whole sequence behind one function ([MaskedEmailCache.refresh],
 * [ExportCache.export]): callers never see the snapshot, so they cannot take it
 * after the fetch, which would pass the check and defeat it.
 */
internal class GenerationGuard {
    private val generation = AtomicLong(0)

    /** Snapshot for a caller about to start the slow work. */
    fun current(): Long = generation.get()

    /** True when no [advance] happened since [snapshot] was taken. */
    fun isCurrent(snapshot: Long): Boolean = snapshot == generation.get()

    /** Invalidates every snapshot taken so far. */
    fun advance() {
        generation.incrementAndGet()
    }
}
