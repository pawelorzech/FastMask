package com.fastmask.domain.hygiene

import com.fastmask.domain.model.EmailState
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
    /** Received mail since the user last reviewed the collection. */
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
    /**
     * Masks in the collection BEFORE archived ones were filtered out.
     *
     * Carried so the screen can tell "you have never made a mask" apart from
     * "you archived every mask you had": both leave [reviewedCount] at zero,
     * but telling a tidy user with 40 archived masks that they have none is a
     * lie about their own account.
     */
    val totalCount: Int = 0,
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
    ): Set<HygieneIssue> {
        if (mask.state == EmailState.DELETED) {
            return emptySet()
        }

        val issues: LinkedHashSet<HygieneIssue> = linkedSetOf()

        if (
            mask.lastMessageAt == null &&
            mask.createdAt != null &&
            Duration.between(mask.createdAt, now) >= thresholds.neverUsedAfter
        ) {
            issues += HygieneIssue.NEVER_USED
        }

        if (
            mask.isActive &&
            mask.lastMessageAt != null &&
            Duration.between(mask.lastMessageAt, now) >= thresholds.dormantAfter
        ) {
            issues += HygieneIssue.DORMANT
        }

        if (mask.description.isNullOrBlank() && mask.forDomain.isNullOrBlank()) {
            issues += HygieneIssue.UNDESCRIBED
        }

        return issues
    }

    /**
     * Reviews the whole collection.
     *
     * @param baseline what the collection looked like when the user last
     *   reviewed it, or null when there is none (first review, demo mode,
     *   unreadable file). Null must degrade to an empty
     *   [HygieneIssue.NEW_ACTIVITY] category, never to an error.
     */
    fun review(
        masks: List<MaskedEmail>,
        now: Instant,
        baseline: HygieneBaseline? = null,
        thresholds: HygieneThresholds = HygieneThresholds.DEFAULT,
    ): MaskHygieneReport {
        val survivingMasks: List<MaskedEmail> = masks.filter { mask ->
            mask.state != EmailState.DELETED
        }
        if (survivingMasks.isEmpty()) {
            return MaskHygieneReport.EMPTY.copy(totalCount = masks.size)
        }

        val groupsByIssue: LinkedHashMap<HygieneIssue, MutableList<MaskedEmail>> = linkedMapOf()
        var healthyCount: Int = 0

        survivingMasks.forEach { mask ->
            val issues: LinkedHashSet<HygieneIssue> = linkedSetOf()
            if (hasNewActivity(mask, baseline)) {
                issues += HygieneIssue.NEW_ACTIVITY
            }
            issues += classify(mask, now, thresholds)

            if (issues.isEmpty()) {
                healthyCount++
            } else {
                issues.forEach { issue ->
                    groupsByIssue.getOrPut(issue) { mutableListOf() } += mask
                }
            }
        }

        val groups: List<HygieneGroup> = HygieneIssue.values().mapNotNull { issue ->
            groupsByIssue[issue]?.takeIf { masksForIssue -> masksForIssue.isNotEmpty() }?.let { masksForIssue ->
                HygieneGroup(issue = issue, masks = masksForIssue)
            }
        }

        return MaskHygieneReport(
            groups = groups,
            reviewedCount = survivingMasks.size,
            healthyCount = healthyCount,
            totalCount = masks.size,
        )
    }

    /**
     * Baseline diffs only make sense for masks that already existed at the last
     * review. Treating a just-created mask as "new activity" would fire the
     * category on every creation and dilute the signal the screen is meant to
     * surface — hence [Map.containsKey] rather than a null lookup, since a
     * known mask with no mail yet is stored as a null value.
     */
    private fun hasNewActivity(
        mask: MaskedEmail,
        baseline: HygieneBaseline?,
    ): Boolean {
        val currentLastMessageAt: Instant = mask.lastMessageAt ?: return false
        if (baseline == null || !baseline.lastMessageAtById.containsKey(mask.id)) return false
        val baselineLastMessageAt: Instant? = baseline.lastMessageAtById[mask.id]
        return baselineLastMessageAt == null || currentLastMessageAt > baselineLastMessageAt
    }
}
