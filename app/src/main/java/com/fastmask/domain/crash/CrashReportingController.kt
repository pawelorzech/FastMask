package com.fastmask.domain.crash

/**
 * Applies [CrashReportingPolicy] to a [CrashReporter]. Called at app start and
 * again every time the user flips the settings switch. Firebase persists a
 * runtime opt-out immediately but documents that `false` takes effect on the
 * next app run; queued-report deletion is requested at the same time.
 */
class CrashReportingController(
    private val reporter: CrashReporter,
    private val isDebugBuild: Boolean,
) {

    /**
     * @param userEnabled the opt-out preference as stored (or its default).
     */
    fun apply(userEnabled: Boolean) {
        // Persist the SDK override first, then request deletion of reports
        // already queued. Firebase applies a false override on the next app run,
        // so the privacy policy must not claim the current process stops instantly.
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
