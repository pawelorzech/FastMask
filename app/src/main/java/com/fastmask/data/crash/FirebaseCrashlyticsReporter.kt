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
 * product is enabled alongside it, and the session-reporting SDK that ships
 * inside Crashlytics is switched off in the manifest
 * (`firebase_sessions_enabled`).
 *
 * A report only leaves on a real crash, but the SDK is not silent at startup
 * while collection is on: `FirebaseCrashlytics.init` unconditionally calls
 * `SettingsController.loadSettingsData`, which fetches Crashlytics' own remote
 * config (carrying the installation ID, device model and OS build), and
 * `IdManager` registers the Firebase installation. Both are gated on
 * `DataCollectionArbiter` — with collection off the config fetch never runs,
 * because `waitForDataCollectionPermission()` never resolves. The one thing
 * that is not gated is `IdManager.getInstallIds()` on session open: it still
 * calls `FirebaseInstallationsApi.getId()`, which is a local read once the
 * installation is registered but will retry registration if it never
 * completed. [setCollectionEnabled] is therefore what the privacy docs mean by
 * "switching it off stops the startup traffic".
 *
 * The SDK handle is resolved lazily, per call, and never in the constructor.
 * `FirebaseCrashlytics.getInstance()` throws `IllegalStateException` when the
 * default `FirebaseApp` was never initialised — which happens on OEM ROMs that
 * strip content providers, in app-cloning frameworks and during direct boot.
 * Resolving it eagerly put that throw inside Hilt member injection, on the main
 * thread, before `super.onCreate()`: the app could not launch at all because of
 * a diagnostics feature the user is allowed to switch off.
 */
class FirebaseCrashlyticsReporter(
    private val crashlytics: () -> FirebaseCrashlytics = { FirebaseCrashlytics.getInstance() },
) : CrashReporter {

    override fun setCollectionEnabled(enabled: Boolean) {
        crashlytics().setCrashlyticsCollectionEnabled(enabled)
    }

    /**
     * Drops reports captured before an opt-out. Fire-and-forget: the SDK
     * returns a Task, but there is nothing useful to do with its result and
     * awaiting it would block the settings toggle on I/O.
     */
    override fun deleteUnsentReports() {
        crashlytics().deleteUnsentReports()
    }
}
