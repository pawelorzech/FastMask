package com.fastmask.domain.review

/**
 * When FastMask may ask for a Play rating. Pure, because the decision is the part worth testing
 * and the Play review flow cannot run off a device.
 *
 * - **[MIN_MASKS] created masks.** Creating an address is the moment the app has just done its job.
 *   Three means the user has seen it work more than once, so the rating is of the app and not of
 *   the install. It also rules out the first launch by construction.
 * - **Once.** Play throttles the flow on its own, and a second ask costs goodwill.
 * - **Never in demo mode.** Demo masks are not real, so the user has not yet got value to rate.
 *
 * Deliberately not a condition: Pro status. A rating prompt tied to a purchase is a review bought
 * with a paywall.
 */
object ReviewPromptPolicy {

    /** Masks created, inclusive, before the prompt is allowed. */
    const val MIN_MASKS: Int = 3

    fun shouldPrompt(createdMasks: Int, alreadyAsked: Boolean, isDemo: Boolean): Boolean =
        !alreadyAsked && !isDemo && createdMasks >= MIN_MASKS
}
