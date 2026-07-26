package com.fastmask.data.crash

import com.fastmask.domain.crash.CrashReporter
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * The single point where the app touches the Firebase Crashlytics SDK.
 *
 * It exposes nothing beyond the two switches [CrashReporter] declares. That is
 * deliberate: the SDK's user-identifier, custom-key and log APIs are one-liners
 * that ship whatever they are handed straight to Google, and the values closest
 * to hand in this app are exactly the ones that must never leave the device —
 * the mask address, the mask description, the site a mask was made for, the
 * owner's own e-mail and the Fastmail API token. With no way to pass them, no
 * future call site can leak them by accident. `CrashReportingPrivacyTest`
 * enforces that this stays the only file naming the SDK.
 *
 * What Crashlytics still sends on a crash is what it derives itself: the stack
 * trace, the app version, the device model and the OS build. No analytics
 * product is enabled alongside it.
 */
class FirebaseCrashlyticsReporter(
    private val crashlytics: FirebaseCrashlytics = FirebaseCrashlytics.getInstance(),
) : CrashReporter {

    override fun setCollectionEnabled(enabled: Boolean) {
        crashlytics.setCrashlyticsCollectionEnabled(enabled)
    }

    /**
     * Drops reports captured before an opt-out. Fire-and-forget: the SDK
     * returns a Task, but there is nothing useful to do with its result and
     * awaiting it would block the settings toggle on I/O.
     */
    override fun deleteUnsentReports() {
        crashlytics.deleteUnsentReports()
    }
}
