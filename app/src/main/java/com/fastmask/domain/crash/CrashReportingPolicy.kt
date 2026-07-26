package com.fastmask.domain.crash

/**
 * Decides whether crashes may be collected at all. Pure, so it is testable
 * without Firebase on the classpath.
 */
object CrashReportingPolicy {

    /**
     * @param isDebugBuild `BuildConfig.DEBUG` — development crashes must never
     *   reach the production console, whatever the user preference says.
     * @param userEnabled the opt-out preference (`true` when the user never
     *   touched the switch).
     */
    fun shouldCollect(isDebugBuild: Boolean, userEnabled: Boolean): Boolean =
        TODO("stub — implemented by the crash reporting change")
}
