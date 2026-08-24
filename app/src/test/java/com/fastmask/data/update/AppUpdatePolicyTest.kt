package com.fastmask.data.update

import org.junit.Assert.assertEquals
import org.junit.Test

class AppUpdatePolicyTest {

    private fun snapshot(
        availability: AppUpdateAvailability = AppUpdateAvailability.AVAILABLE,
        availableVersionCode: Int = 42,
        isFlexibleAllowed: Boolean = true,
        isImmediateAllowed: Boolean = true,
        priority: Int = 0,
        stalenessDays: Int? = 0,
        isDownloaded: Boolean = false,
    ) = AppUpdateSnapshot(
        availability = availability,
        availableVersionCode = availableVersionCode,
        isFlexibleAllowed = isFlexibleAllowed,
        isImmediateAllowed = isImmediateAllowed,
        priority = priority,
        stalenessDays = stalenessDays,
        isDownloaded = isDownloaded,
    )

    private fun decide(
        snapshot: AppUpdateSnapshot,
        dismissedVersionCode: Int = AppUpdatePolicy.NO_VERSION,
        hasPromptedThisProcess: Boolean = false,
    ) = AppUpdatePolicy.decide(snapshot, dismissedVersionCode, hasPromptedThisProcess)

    @Test
    fun `no update available does nothing`() {
        assertEquals(
            AppUpdateDecision.None,
            decide(snapshot(availability = AppUpdateAvailability.NONE))
        )
    }

    @Test
    fun `an ordinary release offers the flexible flow`() {
        assertEquals(
            AppUpdateDecision.Start(AppUpdateFlowType.FLEXIBLE),
            decide(snapshot())
        )
    }

    @Test
    fun `a high priority release blocks`() {
        assertEquals(
            AppUpdateDecision.Start(AppUpdateFlowType.IMMEDIATE),
            decide(snapshot(priority = AppUpdatePolicy.IMMEDIATE_PRIORITY))
        )
    }

    @Test
    fun `priority just below the line stays flexible`() {
        assertEquals(
            AppUpdateDecision.Start(AppUpdateFlowType.FLEXIBLE),
            decide(snapshot(priority = AppUpdatePolicy.IMMEDIATE_PRIORITY - 1))
        )
    }

    @Test
    fun `a stale client blocks even at priority zero`() {
        assertEquals(
            AppUpdateDecision.Start(AppUpdateFlowType.IMMEDIATE),
            decide(snapshot(stalenessDays = AppUpdatePolicy.IMMEDIATE_STALENESS_DAYS))
        )
    }

    @Test
    fun `staleness one day short stays flexible`() {
        assertEquals(
            AppUpdateDecision.Start(AppUpdateFlowType.FLEXIBLE),
            decide(snapshot(stalenessDays = AppUpdatePolicy.IMMEDIATE_STALENESS_DAYS - 1))
        )
    }

    /** Play does not always know how old the update is; unknown must never read as urgent. */
    @Test
    fun `unknown staleness is not urgency`() {
        assertEquals(
            AppUpdateDecision.Start(AppUpdateFlowType.FLEXIBLE),
            decide(snapshot(stalenessDays = null))
        )
    }

    /** Some route to the fix beats none: urgency does not get to end the offer entirely. */
    @Test
    fun `an urgent release Play will not block for falls back to flexible`() {
        assertEquals(
            AppUpdateDecision.Start(AppUpdateFlowType.FLEXIBLE),
            decide(snapshot(priority = 5, isImmediateAllowed = false))
        )
    }

    @Test
    fun `no allowed flow means no offer`() {
        assertEquals(
            AppUpdateDecision.None,
            decide(snapshot(isFlexibleAllowed = false, isImmediateAllowed = false))
        )
    }

    @Test
    fun `a declined version is not offered again`() {
        assertEquals(
            AppUpdateDecision.None,
            decide(snapshot(availableVersionCode = 42), dismissedVersionCode = 42)
        )
    }

    /** A refusal silences one release, never the next one. */
    @Test
    fun `declining one version does not silence the following release`() {
        assertEquals(
            AppUpdateDecision.Start(AppUpdateFlowType.FLEXIBLE),
            decide(snapshot(availableVersionCode = 43), dismissedVersionCode = 42)
        )
    }

    @Test
    fun `the flexible offer is made once per process`() {
        assertEquals(
            AppUpdateDecision.None,
            decide(snapshot(), hasPromptedThisProcess = true)
        )
    }

    /** An urgent update is meant to be insistent: neither guard applies to it. */
    @Test
    fun `an urgent release ignores both anti-nagging guards`() {
        assertEquals(
            AppUpdateDecision.Start(AppUpdateFlowType.IMMEDIATE),
            decide(
                snapshot(availableVersionCode = 42, priority = 5),
                dismissedVersionCode = 42,
                hasPromptedThisProcess = true,
            )
        )
    }

    @Test
    fun `a finished download asks to install whatever else is true`() {
        assertEquals(
            AppUpdateDecision.CompleteInstall,
            decide(
                snapshot(availableVersionCode = 42, isDownloaded = true),
                dismissedVersionCode = 42,
                hasPromptedThisProcess = true,
            )
        )
    }

    /**
     * An interrupted immediate install has to be re-entered — that is the state a user is left in
     * when they back out of the blocking flow, and it is the reason the check runs on every resume.
     */
    @Test
    fun `an interrupted urgent update is resumed`() {
        assertEquals(
            AppUpdateDecision.Start(AppUpdateFlowType.IMMEDIATE),
            decide(snapshot(availability = AppUpdateAvailability.IN_PROGRESS, priority = 5))
        )
    }

    /**
     * A flexible download already running finishes by itself. Turning it into a full-screen block
     * would take the app away from a user who chose the flow that does not.
     */
    @Test
    fun `an in-progress routine update is left alone`() {
        assertEquals(
            AppUpdateDecision.None,
            decide(snapshot(availability = AppUpdateAvailability.IN_PROGRESS))
        )
    }
}
