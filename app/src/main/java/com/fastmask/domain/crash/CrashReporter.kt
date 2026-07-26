package com.fastmask.domain.crash

/**
 * The only sanctioned way for the app to talk to a crash reporting backend.
 *
 * Deliberately tiny: two fire-and-forget calls, no way to attach a user id, a
 * custom key or a log line. Everything the app could pass here would be user
 * data (mask addresses, descriptions, domains, the Fastmail token), so the seam
 * simply does not offer the API. `CrashReportingPrivacyTest` enforces that the
 * concrete Firebase implementation stays behind this interface.
 */
interface CrashReporter {

    /** Turns collection on or off for the rest of the process and future launches. */
    fun setCollectionEnabled(enabled: Boolean)

    /** Drops every report captured but not yet uploaded. */
    fun deleteUnsentReports()
}
