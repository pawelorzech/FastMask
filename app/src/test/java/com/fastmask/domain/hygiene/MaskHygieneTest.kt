package com.fastmask.domain.hygiene

import com.fastmask.domain.model.CachedMasks
import com.fastmask.domain.model.EmailState
import com.fastmask.testutil.mask
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mask hygiene is the one thing Fastmail's own web UI will not tell you: after
 * a year of use you have sixty addresses and no idea which are dead, which
 * never received anything, and which you can no longer identify.
 *
 * Everything asserted here is computed from fields JMAP actually returns.
 * [com.fastmask.domain.model.MaskedEmail] has no message counter, so there is
 * no traffic volume, no "spike" detection, and nothing here pretends otherwise.
 *
 * Time is always passed in. A classifier that read `Instant.now()` would make
 * every boundary test flaky by exactly the runtime of the test.
 */
class MaskHygieneTest {

    private val now: Instant = Instant.parse("2026-07-25T12:00:00Z")
    private val thresholds = HygieneThresholds.DEFAULT

    private fun ago(duration: Duration): Instant = now.minus(duration)
    private fun days(n: Long): Duration = Duration.ofDays(n)

    /** A mask that is fine on every axis, used as the base for single-issue cases. */
    private fun healthy(id: String = "ok") = mask(
        id = id,
        description = "Newsletter",
        forDomain = "example.com",
        createdAt = ago(days(400)),
        lastMessageAt = ago(days(1)),
    )

    // --- defaults ----------------------------------------------------------

    /**
     * The thresholds are product decisions, not implementation detail: 30 days
     * is "the signup either never happened or never mailed me", 180 days is
     * "half a year of silence". Pinning them stops a later refactor from
     * quietly turning the screen into noise.
     */
    @Test
    fun `default thresholds are thirty days unused and six months dormant`() {
        assertEquals(Duration.ofDays(30), thresholds.neverUsedAfter)
        assertEquals(Duration.ofDays(180), thresholds.dormantAfter)
    }

    // --- never used --------------------------------------------------------

    /**
     * The mask you created two minutes ago has no mail yet by definition.
     * Flagging it would put every fresh address on the cleanup list.
     */
    @Test
    fun `a never used mask below the threshold is left alone`() {
        val fresh = mask(
            id = "fresh",
            description = "Just signed up",
            createdAt = ago(days(29)),
            lastMessageAt = null,
        )

        assertEquals(emptySet<HygieneIssue>(), MaskHygiene.classify(fresh, now, thresholds))
    }

    @Test
    fun `a never used mask older than the threshold is flagged`() {
        val stale = mask(
            id = "stale",
            description = "Shop signup",
            createdAt = ago(days(31)),
            lastMessageAt = null,
        )

        assertEquals(setOf(HygieneIssue.NEVER_USED), MaskHygiene.classify(stale, now, thresholds))
    }

    /** Boundary pinned on purpose: exactly the threshold counts as reached. */
    @Test
    fun `exactly thirty days without a message counts as never used`() {
        val exact = mask(
            id = "exact",
            description = "Shop signup",
            createdAt = ago(thresholds.neverUsedAfter),
            lastMessageAt = null,
        )

        assertTrue(HygieneIssue.NEVER_USED in MaskHygiene.classify(exact, now, thresholds))
    }

    @Test
    fun `one second under thirty days is not yet never used`() {
        val justUnder = mask(
            id = "justUnder",
            description = "Shop signup",
            createdAt = ago(thresholds.neverUsedAfter.minusSeconds(1)),
            lastMessageAt = null,
        )

        assertFalse(HygieneIssue.NEVER_USED in MaskHygiene.classify(justUnder, now, thresholds))
    }

    /** PENDING is the state of a mask that has never received mail — the exact case. */
    @Test
    fun `a pending mask past the threshold is never used`() {
        val pending = mask(
            id = "pending",
            state = EmailState.PENDING,
            description = "Shop signup",
            createdAt = ago(days(90)),
            lastMessageAt = null,
        )

        assertTrue(HygieneIssue.NEVER_USED in MaskHygiene.classify(pending, now, thresholds))
    }

    // --- dormant -----------------------------------------------------------

    @Test
    fun `a mask that received mail yesterday is healthy`() {
        assertEquals(emptySet<HygieneIssue>(), MaskHygiene.classify(healthy(), now, thresholds))
    }

    @Test
    fun `seven months of silence makes an active mask dormant`() {
        val quiet = mask(
            id = "quiet",
            description = "Old forum",
            createdAt = ago(days(700)),
            lastMessageAt = ago(days(210)),
        )

        assertEquals(setOf(HygieneIssue.DORMANT), MaskHygiene.classify(quiet, now, thresholds))
    }

    @Test
    fun `exactly six months of silence counts as dormant`() {
        val exact = mask(
            id = "exact",
            description = "Old forum",
            createdAt = ago(days(700)),
            lastMessageAt = ago(thresholds.dormantAfter),
        )

        assertTrue(HygieneIssue.DORMANT in MaskHygiene.classify(exact, now, thresholds))
    }

    @Test
    fun `one second under six months is not yet dormant`() {
        val justUnder = mask(
            id = "justUnder",
            description = "Old forum",
            createdAt = ago(days(700)),
            lastMessageAt = ago(thresholds.dormantAfter.minusSeconds(1)),
        )

        assertFalse(HygieneIssue.DORMANT in MaskHygiene.classify(justUnder, now, thresholds))
    }

    /**
     * The only action offered for a dormant mask is "turn it off". Proposing
     * that for a mask that is already off is a suggestion the user cannot act
     * on, and it inflates the cleanup count with work that does not exist.
     */
    @Test
    fun `an already disabled mask is never proposed as dormant`() {
        val off = mask(
            id = "off",
            state = EmailState.DISABLED,
            description = "Old forum",
            createdAt = ago(days(700)),
            lastMessageAt = ago(days(400)),
        )

        assertFalse(HygieneIssue.DORMANT in MaskHygiene.classify(off, now, thresholds))
    }

    /** Archived masks are done. They are not cleanup work of any kind. */
    @Test
    fun `an archived mask has no hygiene issues at all`() {
        val archived = mask(
            id = "archived",
            state = EmailState.DELETED,
            description = null,
            forDomain = null,
            createdAt = ago(days(700)),
            lastMessageAt = null,
        )

        assertEquals(emptySet<HygieneIssue>(), MaskHygiene.classify(archived, now, thresholds))
    }

    // --- undescribed -------------------------------------------------------

    @Test
    fun `no description and no domain makes a mask unidentifiable`() {
        val anonymous = mask(
            id = "anon",
            description = null,
            forDomain = null,
            createdAt = ago(days(1)),
            lastMessageAt = ago(days(1)),
        )

        assertEquals(setOf(HygieneIssue.UNDESCRIBED), MaskHygiene.classify(anonymous, now, thresholds))
    }

    @Test
    fun `a description alone is enough context`() {
        val described = mask(
            id = "described",
            description = "Bank alerts",
            forDomain = null,
            createdAt = ago(days(1)),
            lastMessageAt = ago(days(1)),
        )

        assertFalse(HygieneIssue.UNDESCRIBED in MaskHygiene.classify(described, now, thresholds))
    }

    @Test
    fun `a domain alone is enough context`() {
        val domainOnly = mask(
            id = "domainOnly",
            description = null,
            forDomain = "bank.example",
            createdAt = ago(days(1)),
            lastMessageAt = ago(days(1)),
        )

        assertFalse(HygieneIssue.UNDESCRIBED in MaskHygiene.classify(domainOnly, now, thresholds))
    }

    /** The API happily stores an empty string; a blank label identifies nothing. */
    @Test
    fun `blank description and blank domain count as missing`() {
        val blank = mask(
            id = "blank",
            description = "   ",
            forDomain = "",
            createdAt = ago(days(1)),
            lastMessageAt = ago(days(1)),
        )

        assertEquals(setOf(HygieneIssue.UNDESCRIBED), MaskHygiene.classify(blank, now, thresholds))
    }

    // --- overlapping categories -------------------------------------------

    /**
     * Categories are not exclusive. A nameless address that never received
     * anything is the worst case in the collection and has to show up in both
     * lists — bucketing it into one would hide it from whichever list the user
     * happens to open.
     */
    @Test
    fun `a mask can hold several issues at once`() {
        val worst = mask(
            id = "worst",
            description = null,
            forDomain = null,
            createdAt = ago(days(200)),
            lastMessageAt = null,
        )

        assertEquals(
            setOf(HygieneIssue.NEVER_USED, HygieneIssue.UNDESCRIBED),
            MaskHygiene.classify(worst, now, thresholds),
        )
    }

    // --- missing createdAt -------------------------------------------------

    /**
     * `createdAt` is nullable in the model and the JMAP parser degrades an
     * unparseable timestamp to null. Without an age there is no honest way to
     * say "old enough to be dead", so the verdict is withheld rather than
     * guessed — and nothing throws.
     */
    @Test
    fun `a missing createdAt cannot support the never used verdict`() {
        val undated = mask(
            id = "undated",
            description = "Some service",
            createdAt = null,
            lastMessageAt = null,
        )

        assertEquals(emptySet<HygieneIssue>(), MaskHygiene.classify(undated, now, thresholds))
    }

    @Test
    fun `a missing createdAt still allows the undescribed verdict`() {
        val undated = mask(
            id = "undated",
            description = null,
            forDomain = null,
            createdAt = null,
            lastMessageAt = null,
        )

        assertEquals(setOf(HygieneIssue.UNDESCRIBED), MaskHygiene.classify(undated, now, thresholds))
    }

    // --- collection summary ------------------------------------------------

    @Test
    fun `an empty collection produces an empty report instead of failing`() {
        val report = MaskHygiene.review(emptyList(), now, baseline = null, thresholds = thresholds)

        assertTrue(report.isClean)
        assertEquals(emptyList<HygieneGroup>(), report.groups)
        assertEquals(0, report.reviewedCount)
        assertEquals(0, report.healthyCount)
    }

    /** The "nothing to do" state the screen renders instead of empty lists. */
    @Test
    fun `a collection with no issues reports itself clean`() {
        val report = MaskHygiene.review(
            listOf(healthy("a"), healthy("b"), healthy("c")),
            now,
            baseline = null,
            thresholds = thresholds,
        )

        assertTrue(report.isClean)
        assertEquals(3, report.reviewedCount)
        assertEquals(3, report.healthyCount)
    }

    @Test
    fun `counts match the classification of each mask`() {
        val masks = listOf(
            healthy("healthy"),
            mask("unused", description = "Shop", createdAt = ago(days(60)), lastMessageAt = null),
            mask("quiet", description = "Forum", createdAt = ago(days(700)), lastMessageAt = ago(days(400))),
            mask("anon", createdAt = ago(days(1)), lastMessageAt = ago(days(1))),
        )

        val report = MaskHygiene.review(masks, now, baseline = null, thresholds = thresholds)

        assertEquals(1, report.count(HygieneIssue.NEVER_USED))
        assertEquals(1, report.count(HygieneIssue.DORMANT))
        assertEquals(1, report.count(HygieneIssue.UNDESCRIBED))
        assertEquals(listOf("unused"), report.masksFor(HygieneIssue.NEVER_USED).map { it.id })
        assertEquals(4, report.reviewedCount)
        assertEquals(1, report.healthyCount)
        assertFalse(report.isClean)
    }

    /**
     * A mask in two categories is one unhealthy mask, not two. The headline
     * count has to match the number of addresses the user has to deal with.
     */
    @Test
    fun `a mask in two categories is counted once against the healthy total`() {
        val masks = listOf(
            healthy("healthy"),
            mask("worst", createdAt = ago(days(200)), lastMessageAt = null),
        )

        val report = MaskHygiene.review(masks, now, baseline = null, thresholds = thresholds)

        assertEquals(2, report.reviewedCount)
        assertEquals(1, report.healthyCount)
        assertEquals(listOf("worst"), report.masksFor(HygieneIssue.NEVER_USED).map { it.id })
        assertEquals(listOf("worst"), report.masksFor(HygieneIssue.UNDESCRIBED).map { it.id })
    }

    /** Archived masks are out of scope entirely — not reviewed, not "healthy". */
    @Test
    fun `archived masks are excluded from the review`() {
        val masks = listOf(
            healthy("live"),
            mask("archived", state = EmailState.DELETED, createdAt = ago(days(700)), lastMessageAt = null),
        )

        val report = MaskHygiene.review(masks, now, baseline = null, thresholds = thresholds)

        assertTrue(report.isClean)
        assertEquals(1, report.reviewedCount)
        assertEquals(1, report.healthyCount)
    }

    /**
     * Groups come out in [HygieneIssue] declaration order and empty ones are
     * dropped, so the screen can render `groups` straight down the page.
     */
    @Test
    fun `groups follow the declared priority and skip empty categories`() {
        val baseline = CachedMasks(
            masks = listOf(mask("busy", description = "Bank", createdAt = ago(days(400)), lastMessageAt = ago(days(30)))),
            cachedAt = ago(days(2)),
        )
        val masks = listOf(
            mask("anon", createdAt = ago(days(1)), lastMessageAt = ago(days(1))),
            mask("unused", description = "Shop", createdAt = ago(days(60)), lastMessageAt = null),
            mask("busy", description = "Bank", createdAt = ago(days(400)), lastMessageAt = ago(days(1))),
        )

        val report = MaskHygiene.review(masks, now, baseline = baseline, thresholds = thresholds)

        // DORMANT has no members here and must not appear as an empty group.
        assertEquals(
            listOf(HygieneIssue.NEW_ACTIVITY, HygieneIssue.NEVER_USED, HygieneIssue.UNDESCRIBED),
            report.groups.map { it.issue },
        )
    }

    // --- new activity vs the cached snapshot -------------------------------

    /**
     * The snapshot is [com.fastmask.data.local.MaskedEmailCache], written on
     * every successful fetch, so "new activity" means "since the last
     * successful refresh". No snapshot (first run, demo mode, unreadable file)
     * is a missing baseline, not an error: the category is simply empty.
     */
    @Test
    fun `a missing snapshot yields an empty new activity category not a failure`() {
        val report = MaskHygiene.review(
            listOf(healthy("a")),
            now,
            baseline = null,
            thresholds = thresholds,
        )

        assertEquals(0, report.count(HygieneIssue.NEW_ACTIVITY))
        assertFalse(report.groups.any { it.issue == HygieneIssue.NEW_ACTIVITY })
    }

    /** A corrupt cache reads back as an empty snapshot; the screen still works. */
    @Test
    fun `an empty snapshot yields an empty new activity category`() {
        val report = MaskHygiene.review(
            listOf(healthy("a")),
            now,
            baseline = CachedMasks(emptyList(), ago(days(1))),
            thresholds = thresholds,
        )

        assertEquals(0, report.count(HygieneIssue.NEW_ACTIVITY))
    }

    @Test
    fun `only masks whose last message moved since the snapshot count as new activity`() {
        val baseline = CachedMasks(
            masks = listOf(
                mask("moved", description = "Bank", createdAt = ago(days(400)), lastMessageAt = ago(days(30))),
                mask("still", description = "Forum", createdAt = ago(days(400)), lastMessageAt = ago(days(30))),
            ),
            cachedAt = ago(days(2)),
        )
        val masks = listOf(
            mask("moved", description = "Bank", createdAt = ago(days(400)), lastMessageAt = ago(days(1))),
            mask("still", description = "Forum", createdAt = ago(days(400)), lastMessageAt = ago(days(30))),
        )

        val report = MaskHygiene.review(masks, now, baseline = baseline, thresholds = thresholds)

        assertEquals(listOf("moved"), report.masksFor(HygieneIssue.NEW_ACTIVITY).map { it.id })
    }

    @Test
    fun `a first ever message on a known mask is new activity`() {
        val baseline = CachedMasks(
            masks = listOf(mask("first", description = "Shop", createdAt = ago(days(40)), lastMessageAt = null)),
            cachedAt = ago(days(2)),
        )
        val masks = listOf(
            mask("first", description = "Shop", createdAt = ago(days(40)), lastMessageAt = ago(days(1))),
        )

        val report = MaskHygiene.review(masks, now, baseline = baseline, thresholds = thresholds)

        assertEquals(listOf("first"), report.masksFor(HygieneIssue.NEW_ACTIVITY).map { it.id })
    }

    /**
     * A mask created since the snapshot is new, which is not the same claim as
     * "this address started receiving mail". Reporting it here would make the
     * category fire on every mask the user just made themselves.
     */
    @Test
    fun `a mask absent from the snapshot is not reported as new activity`() {
        val baseline = CachedMasks(
            masks = listOf(mask("old", description = "Bank", createdAt = ago(days(400)), lastMessageAt = ago(days(30)))),
            cachedAt = ago(days(2)),
        )
        val masks = listOf(
            mask("old", description = "Bank", createdAt = ago(days(400)), lastMessageAt = ago(days(30))),
            mask("brandNew", description = "Shop", createdAt = ago(days(1)), lastMessageAt = ago(days(1))),
        )

        val report = MaskHygiene.review(masks, now, baseline = baseline, thresholds = thresholds)

        assertEquals(0, report.count(HygieneIssue.NEW_ACTIVITY))
    }

    /** A mask that still has no mail at all cannot have new activity. */
    @Test
    fun `a mask with no message in either snapshot is not new activity`() {
        val baseline = CachedMasks(
            masks = listOf(mask("silent", description = "Shop", createdAt = ago(days(60)), lastMessageAt = null)),
            cachedAt = ago(days(2)),
        )
        val masks = listOf(
            mask("silent", description = "Shop", createdAt = ago(days(60)), lastMessageAt = null),
        )

        val report = MaskHygiene.review(masks, now, baseline = baseline, thresholds = thresholds)

        assertEquals(0, report.count(HygieneIssue.NEW_ACTIVITY))
        // ...but it is still the dead address it was before.
        assertEquals(listOf("silent"), report.masksFor(HygieneIssue.NEVER_USED).map { it.id })
    }

    /**
     * Mail arriving on a mask the user had left dormant is exactly the case
     * worth surfacing: it both moved and is still stale by the age rule.
     */
    @Test
    fun `new activity stacks with the other verdicts`() {
        val baseline = CachedMasks(
            masks = listOf(mask("waking", createdAt = ago(days(700)), lastMessageAt = ago(days(400)))),
            cachedAt = ago(days(2)),
        )
        val masks = listOf(
            mask("waking", createdAt = ago(days(700)), lastMessageAt = ago(days(300))),
        )

        val report = MaskHygiene.review(masks, now, baseline = baseline, thresholds = thresholds)

        assertEquals(listOf("waking"), report.masksFor(HygieneIssue.NEW_ACTIVITY).map { it.id })
        assertEquals(listOf("waking"), report.masksFor(HygieneIssue.DORMANT).map { it.id })
        assertEquals(listOf("waking"), report.masksFor(HygieneIssue.UNDESCRIBED).map { it.id })
        assertEquals(0, report.healthyCount)
    }
}
