package com.fastmask.data.update

/**
 * What Play reports about an available update, with every Play type left at the door.
 *
 * The Play Core types are not stable to observe and not constructible in a unit test, so the
 * decision they feed is written against this instead. Everything the policy is allowed to consider
 * is a field here; anything absent from this class is, by construction, something the decision
 * cannot depend on.
 */
data class AppUpdateSnapshot(
    val availability: AppUpdateAvailability,
    /** The versionCode Play would install. Zero when Play has no update to describe. */
    val availableVersionCode: Int,
    val isFlexibleAllowed: Boolean,
    val isImmediateAllowed: Boolean,
    /**
     * The release's `inAppUpdatePriority`, 0–5. It is *not* settable in the Play Console UI: it is
     * fixed at publish time through `Edits.tracks.releases` on the Play Developer API. A release
     * published without it arrives here as 0, so a build that never sets it can only ever reach
     * the immediate flow through staleness.
     */
    val priority: Int,
    /** Days since the update became available on Play, or null when Play does not know. */
    val stalenessDays: Int?,
    /** A flexible download that has finished and is waiting for [AppUpdateDecision.CompleteInstall]. */
    val isDownloaded: Boolean,
)

/** Play's `UpdateAvailability`, narrowed to the three cases that change what FastMask does. */
enum class AppUpdateAvailability {
    NONE,
    AVAILABLE,

    /**
     * Play is already part-way through an update this app started — the state a killed process
     * comes back to. An immediate flow interrupted this way leaves the install half-done and
     * *must* be re-entered, which is the whole reason the check runs on every resume rather than
     * only on a cold start.
     */
    IN_PROGRESS,
}

enum class AppUpdateFlowType { FLEXIBLE, IMMEDIATE }

sealed interface AppUpdateDecision {
    data object None : AppUpdateDecision

    /** Bytes are already on the device; all that is left is asking to restart. */
    data object CompleteInstall : AppUpdateDecision

    data class Start(val type: AppUpdateFlowType) : AppUpdateDecision
}

/**
 * When to offer an update, and how loudly.
 *
 * Two flows exist and they are not interchangeable. **Flexible** downloads in the background and
 * leaves the app usable, so it costs the user nothing but a tap; it is the default for every
 * ordinary release. **Immediate** is Play's own full-screen blocking flow — the app is unusable
 * until the update lands — which is a serious thing to do to someone mid-way through creating a
 * mask, and is reserved for a release that is either marked urgent at publish time or old enough
 * that the user is running something FastMask no longer stands behind.
 *
 * The two guards against nagging live outside this function, because they are state rather than
 * policy: one offer per process, and a version the user already declined. Both are deliberately
 * ignored for an immediate update — an urgent release is meant to re-prompt on every launch, which
 * is the only thing that separates it from a flexible one.
 */
object AppUpdatePolicy {

    /**
     * Play documents 0–5 with no meaning attached to any value; 4 is Google's own suggested line
     * between "worth interrupting for" and "not". A release only reaches the blocking flow if a
     * human deliberately published it at 4 or 5.
     */
    const val IMMEDIATE_PRIORITY = 4

    /**
     * Two weeks. Past that the user is running a build that has been superseded for long enough
     * that a bug they hit may already be fixed — and the flexible offer has, by then, been shown
     * and ignored more than once.
     */
    const val IMMEDIATE_STALENESS_DAYS = 14

    /** No version has been declined. Play version codes start at 1, so 0 cannot collide. */
    const val NO_VERSION = 0

    fun isUrgent(snapshot: AppUpdateSnapshot): Boolean =
        snapshot.priority >= IMMEDIATE_PRIORITY ||
            (snapshot.stalenessDays ?: 0) >= IMMEDIATE_STALENESS_DAYS

    fun decide(
        snapshot: AppUpdateSnapshot,
        dismissedVersionCode: Int,
        hasPromptedThisProcess: Boolean,
    ): AppUpdateDecision {
        // Ahead of everything else, including availability: a finished download is bytes the user
        // already paid for in data and consented to. Leaving them uninstalled is the one outcome
        // with a cost and no benefit.
        if (snapshot.isDownloaded) return AppUpdateDecision.CompleteInstall

        val urgent = isUrgent(snapshot)
        return when (snapshot.availability) {
            AppUpdateAvailability.NONE -> AppUpdateDecision.None

            // A flexible download already running needs nothing from us — it finishes on its own
            // and resurfaces as isDownloaded. Only the blocking flow has to be re-entered, and
            // only when the same rules that started it still say it is urgent; re-entering an
            // immediate flow for a routine release would turn a background download into a
            // full-screen block the user never agreed to.
            AppUpdateAvailability.IN_PROGRESS ->
                if (urgent && snapshot.isImmediateAllowed) {
                    AppUpdateDecision.Start(AppUpdateFlowType.IMMEDIATE)
                } else {
                    AppUpdateDecision.None
                }

            AppUpdateAvailability.AVAILABLE -> when {
                urgent && snapshot.isImmediateAllowed ->
                    AppUpdateDecision.Start(AppUpdateFlowType.IMMEDIATE)

                // Play can refuse a flow type per device and per update (a device short on
                // storage, an update Play will only install immediately). An urgent release whose
                // immediate flow is not allowed falls back to flexible rather than to nothing:
                // some route to the fix beats none.
                !snapshot.isFlexibleAllowed -> AppUpdateDecision.None
                hasPromptedThisProcess -> AppUpdateDecision.None
                dismissedVersionCode == snapshot.availableVersionCode -> AppUpdateDecision.None
                else -> AppUpdateDecision.Start(AppUpdateFlowType.FLEXIBLE)
            }
        }
    }
}
