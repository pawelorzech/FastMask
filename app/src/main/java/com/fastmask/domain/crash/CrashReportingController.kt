package com.fastmask.domain.crash

/**
 * Applies [CrashReportingPolicy] to a [CrashReporter]. Called at app start and
 * again every time the user flips the settings switch, so the change takes
 * effect without a restart.
 */
class CrashReportingController(
    private val reporter: CrashReporter,
    private val isDebugBuild: Boolean,
) {

    /**
     * @param userEnabled the opt-out preference as stored (or its default).
     */
    fun apply(userEnabled: Boolean) {
        // Order is load-bearing: collection stops first, then the queue is
        // purged. Reversed, a crash landing between the two calls would be
        // written back after the purge and uploaded despite the opt-out.
        reporter.setCollectionEnabled(
            CrashReportingPolicy.shouldCollect(
                isDebugBuild = isDebugBuild,
                userEnabled = userEnabled,
            )
        )

        // Driven by the preference, not by the policy result: a debug build
        // already collects nothing, but an explicit opt-out still has to drop
        // whatever an earlier release build left queued on the device.
        if (!userEnabled) {
            reporter.deleteUnsentReports()
        }
    }
}
