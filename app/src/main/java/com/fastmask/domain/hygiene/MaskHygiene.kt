package com.fastmask.domain.hygiene

import com.fastmask.domain.model.CachedMasks
import com.fastmask.domain.model.MaskedEmail
import java.time.Duration
import java.time.Instant

/**
 * STUB — contract only. The implementation is deliberately absent so the tests
 * in `MaskHygieneTest` fail red until someone writes it.
 *
 * "Mask hygiene" is the review of a whole mask collection: which addresses are
 * dead, which were never used, which can no longer be identified. Everything
 * here is computed from fields the JMAP API actually returns — there is no
 * message counter on [MaskedEmail], so no traffic statistics are possible and
 * none are attempted.
 */

/**
 * A single hygiene verdict about a mask. Declaration order IS the display
 * priority used by [MaskHygieneReport.groups]: what changed since the user
 * last looked comes first, then the strongest cleanup signals, then cosmetics.
 */
enum class HygieneIssue {
    /** Received mail since the last successful refresh (cache snapshot diff). */
    NEW_ACTIVITY,

    /** No message ever, and old enough that none is coming. */
    NEVER_USED,

    /** Still accepting mail, but silent for a long time. */
    DORMANT,

    /** Neither a description nor a domain — impossible to identify. */
    UNDESCRIBED,
}

/**
 * How old "old" is. Injected rather than hardcoded so the boundaries are
 * testable and a future settings screen can move them.
 */
data class HygieneThresholds(
    val neverUsedAfter: Duration = Duration.ofDays(30),
    val dormantAfter: Duration = Duration.ofDays(180),
) {
    companion object {
        val DEFAULT = HygieneThresholds()
    }
}

/** One category with the masks that fell into it, in the report's order. */
data class HygieneGroup(
    val issue: HygieneIssue,
    val masks: List<MaskedEmail>,
)

/**
 * The whole-collection verdict. Empty groups are omitted, so [isClean] is the
 * "nothing to do here" signal the empty state renders.
 */
data class MaskHygieneReport(
    val groups: List<HygieneGroup> = emptyList(),
    /** Masks actually reviewed — archived ones are out of scope. */
    val reviewedCount: Int = 0,
    /** Reviewed masks with no issue at all. */
    val healthyCount: Int = 0,
) {
    val isClean: Boolean get() = groups.isEmpty()

    fun masksFor(issue: HygieneIssue): List<MaskedEmail> =
        groups.firstOrNull { it.issue == issue }?.masks.orEmpty()

    fun count(issue: HygieneIssue): Int = masksFor(issue).size

    companion object {
        val EMPTY = MaskHygieneReport()
    }
}

object MaskHygiene {

    /**
     * Verdicts for a single mask. Pure: the caller supplies [now], nothing
     * reads the system clock. Never returns [HygieneIssue.NEW_ACTIVITY] —
     * that one needs the previous snapshot and lives in [review].
     */
    fun classify(
        mask: MaskedEmail,
        now: Instant,
        thresholds: HygieneThresholds = HygieneThresholds.DEFAULT,
    ): Set<HygieneIssue> = TODO("mask hygiene classification not implemented")

    /**
     * Reviews the whole collection.
     *
     * @param baseline the last snapshot the app persisted, or null when there
     *   is none (first run, demo mode, unreadable cache). Null must degrade to
     *   an empty [HygieneIssue.NEW_ACTIVITY] category, never to an error.
     */
    fun review(
        masks: List<MaskedEmail>,
        now: Instant,
        baseline: CachedMasks? = null,
        thresholds: HygieneThresholds = HygieneThresholds.DEFAULT,
    ): MaskHygieneReport = TODO("mask hygiene review not implemented")
}
