package com.fastmask.domain.usecase

/**
 * The one way into demo mode.
 *
 * Entering the demo is not a single flag: the seed list has to be restored,
 * the app mode flipped and the tutorial flag reset, in that order. That
 * sequence lived inside `WelcomeViewModel`, which made "add a demo exit to the
 * login screen" an invitation to copy it. It lives behind this interface
 * instead, so every entry point runs the identical mechanism.
 */
interface DemoModeActivator {
    /** Resets the demo data and switches the app into demo mode. */
    suspend fun activate()
}
