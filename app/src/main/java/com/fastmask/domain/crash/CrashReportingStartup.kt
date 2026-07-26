package com.fastmask.domain.crash

import kotlinx.coroutines.CancellationException

/**
 * Reads the stored opt-out preference and hands it to the [CrashReportingController]
 * at app start.
 *
 * Two invariants, both of which used to be violated:
 *
 *  1. **It never throws.** It runs from `Application.onCreate`, so an exception
 *     escaping here reaches the thread's uncaught handler and kills the process
 *     before the app draws a frame — for a diagnostics feature the user is
 *     explicitly allowed to switch off. Both the read *and* the SDK call are
 *     guarded, because the crash reporting SDK initialises itself lazily on
 *     that call and is the more likely of the two to blow up — on a device
 *     where `FirebaseInitProvider` never ran (OEM ROMs that strip content
 *     providers, app-cloning frameworks, direct boot) resolving the SDK handle
 *     throws `IllegalStateException`.
 *
 *  2. **An unreadable preference is not a decision.** When the store cannot be
 *     read, the SDK is left alone rather than reset to the default. Crashlytics
 *     persists its own collection flag, so "leave it alone" means the user's
 *     last explicit choice keeps applying; forcing the default would silently
 *     re-enable collection for someone who had opted out.
 *
 * Both collaborators are supplied lazily so that constructing this object — which
 * happens during Hilt member injection, on the main thread, before
 * `super.onCreate()` — touches neither DataStore nor Firebase.
 */
class CrashReportingStartup(
    private val readPreference: suspend () -> CrashReportingPreference,
    private val controller: () -> CrashReportingController,
    private val onFailure: (Throwable) -> Unit = {},
) {

    /**
     * @return what the store turned out to say, for logging and tests. Never
     *   throws anything except [CancellationException], which belongs to the
     *   caller's coroutine and must keep propagating.
     */
    suspend fun apply(): CrashReportingPreference {
        val preference = try {
            readPreference()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            onFailure(error)
            CrashReportingPreference.Unreadable
        }

        val enabled = when (preference) {
            is CrashReportingPreference.Stored -> preference.enabled
            CrashReportingPreference.Missing -> CrashReportingPolicy.DEFAULT_ENABLED
            // Deliberately no call at all: see invariant 2 above.
            CrashReportingPreference.Unreadable -> return preference
        }

        try {
            controller().apply(enabled)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            onFailure(error)
        }

        return preference
    }
}
