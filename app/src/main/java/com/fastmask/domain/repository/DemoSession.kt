package com.fastmask.domain.repository

/**
 * Control over the lifetime of the in-memory demo data.
 *
 * The demo repository is a process-lifetime singleton, so masks created or
 * archived during a demo outlive the demo itself: signing out and tapping
 * "Try demo" again used to reopen the previous session's mutated list, even
 * though the demo is meant to start from the same pristine seed every time.
 * Entering demo mode calls [reset] to make that true.
 */
interface DemoSession {
    /** Restores the demo list to its seed contents. */
    fun reset()
}
