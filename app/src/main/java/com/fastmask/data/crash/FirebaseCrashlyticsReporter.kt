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
 * trace, the app and OS version, the device model, and device state it reads at
 * the moment of the crash (free memory and disk, battery, orientation, and
 * more). The set is Google's to define, which is why the privacy policy names a
 * category and links to Firebase rather than pretending to enumerate it. No
 * analytics product is enabled alongside it, and the session-reporting SDK that
 * ships inside Crashlytics is switched off in the manifest
 * (`firebase_sessions_enabled`).
 *
 * A report only leaves on a real crash, but the SDK is not silent at startup
 * while collection is on: `FirebaseCrashlytics.init` calls
 * `SettingsController.loadSettingsData`, which fetches Crashlytics' own remote
 * config (carrying the installation ID, device model and OS build), and
 * `IdManager` registers the Firebase installation. Both are gated on
 * `DataCollectionArbiter`, verified in 19.2.1 bytecode:
 *
 *  - the config fetch waits on `waitForDataCollectionPermission()`, which never
 *    resolves once collection is off;
 *  - `IdManager.getInstallIds()` branches on
 *    `isAutomaticDataCollectionEnabled()` and returns
 *    `InstallIds.createWithoutFid(...)` — a locally generated value — without
 *    ever touching `FirebaseInstallationsApi`.
 *
 * So an opt-out leaves no residual registration, and [setCollectionEnabled] is
 * the whole of what the privacy docs mean by "switching it off stops the
 * startup traffic". Re-verify both branches when the Firebase BOM moves.
 *
 * The SDK handle is resolved lazily, per call, and never in the constructor.
 * `FirebaseCrashlytics.getInstance()` throws `IllegalStateException` when the
 * default `FirebaseApp` was never initialised — which happens on OEM ROMs that
 * strip content providers, in app-cloning frameworks and during direct boot.
 * Resolving it eagerly put that throw inside Hilt member injection, on the main
 * thread, before `super.onCreate()`: the app could not launch at all because of
 * a diagnostics feature the user is allowed to switch off.
 */
class FirebaseCrashlyticsReporter : CrashReporter {

    override fun setCollectionEnabled(enabled: Boolean) {
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(enabled)
    }

    /**
     * Drops reports captured before an opt-out. Fire-and-forget: the SDK
     * returns a Task, but there is nothing useful to do with its result and
     * awaiting it would block the settings toggle on I/O.
     */
    override fun deleteUnsentReports() {
        FirebaseCrashlytics.getInstance().deleteUnsentReports()
    }
}
