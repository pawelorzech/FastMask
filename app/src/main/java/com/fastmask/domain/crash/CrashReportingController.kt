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
    fun apply(userEnabled: Boolean): Unit =
        TODO("stub — implemented by the crash reporting change")
}
