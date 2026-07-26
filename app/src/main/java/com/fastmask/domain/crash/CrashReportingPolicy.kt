package com.fastmask.domain.crash

/**
 * Decides whether crashes may be collected at all. Pure, so it is testable
 * without Firebase on the classpath.
 */
object CrashReportingPolicy {

    /**
     * Opt-out: on unless the user said otherwise. Applies to a fresh install and
     * to every installation that updates into the version introducing the key.
     */
    const val DEFAULT_ENABLED = true

    /**
     * @param isDebugBuild `BuildConfig.DEBUG` — development crashes must never
     *   reach the production console, whatever the user preference says.
     * @param userEnabled the opt-out preference (`true` when the user never
     *   touched the switch).
     */
    fun shouldCollect(isDebugBuild: Boolean, userEnabled: Boolean): Boolean =
        !isDebugBuild && userEnabled
}
