package com.fastmask.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The "3 lat temu" bug, split in two.
 *
 * `RelativeTime.format` did the bucketing and the wording in one expression:
 * `getString(R.string.time_year_ago, diff / YEAR)`. That works for English,
 * whose translations are abbreviations ("%dy ago") and therefore invariant, and
 * it is wrong the moment a locale spells the unit out — Polish shipped
 * "%d lat temu", which is the genitive plural, correct only for 5 and above.
 * A mask created three years ago read "Utworzono 3 lat temu".
 *
 * `getString` cannot fix that: only `getQuantityString(id, count, count)`
 * consults the language's plural rules, and it needs the COUNT as a value
 * rather than baked into a pre-formatted string. So the bucketing moves here,
 * to a pure function, and the wording moves to the resource layer.
 *
 * These tests pin the thresholds, which must not shift while the wording is
 * reworked: every boundary below already governs what a user sees today.
 *
 * `RelativeTime.format` is expected to be rewritten on top of
 * [RelativeTimeBuckets.of] — it stays the Context-facing entry point, this is
 * the part that can be tested.
 */
class RelativeTimeBucketTest {

    private val minute = 60L
    private val hour = 60L * 60
    private val day = 24L * hour
    private val week = 7 * day
    private val month = 30 * day
    private val year = 365 * day

    /** now is fixed; the timestamp is placed [ago] seconds before it. */
    private fun bucketOf(ago: Long, now: Long = 1_700_000_000L) =
        RelativeTimeBuckets.of(epochSecond = now - ago, nowSec = now)

    // --- the two wordless buckets -------------------------------------------

    @Test
    fun `no timestamp at all is never`() {
        val bucket = RelativeTimeBuckets.of(epochSecond = null, nowSec = 1_700_000_000L)
        assertEquals(RelativeTimeUnit.NEVER, bucket.unit)
        assertEquals("NEVER takes no number", 0L, bucket.count)
    }

    @Test
    fun `under a minute is just now`() {
        assertEquals(RelativeTimeUnit.JUST_NOW, bucketOf(0).unit)
        assertEquals(RelativeTimeUnit.JUST_NOW, bucketOf(1).unit)
        assertEquals(RelativeTimeUnit.JUST_NOW, bucketOf(59).unit)
        assertEquals("JUST_NOW takes no number", 0L, bucketOf(30).count)
    }

    @Test
    fun `a timestamp in the future reads as just now, never as a negative count`() {
        // Fastmail's clock and the device's disagree by seconds; a mask created
        // "in 4 seconds" must not render "-1 minutes ago", and must never fall
        // into a counted bucket with a zero or negative number.
        val now = 1_700_000_000L
        listOf(1L, 59L, 3 * hour, 400 * day).forEach { ahead ->
            val bucket = RelativeTimeBuckets.of(epochSecond = now + ahead, nowSec = now)
            assertEquals(
                "a timestamp $ahead s in the future is not JUST_NOW",
                RelativeTimeUnit.JUST_NOW,
                bucket.unit,
            )
            assertEquals(0L, bucket.count)
        }
    }

    // --- boundaries ----------------------------------------------------------

    @Test
    fun `a minute is where the count starts`() {
        assertEquals(RelativeTimeBucket(RelativeTimeUnit.MINUTES, 1), bucketOf(minute))
        assertEquals(RelativeTimeBucket(RelativeTimeUnit.MINUTES, 1), bucketOf(minute + 59))
        assertEquals(RelativeTimeBucket(RelativeTimeUnit.MINUTES, 59), bucketOf(hour - 1))
    }

    @Test
    fun `an hour ends the minutes`() {
        assertEquals(RelativeTimeBucket(RelativeTimeUnit.HOURS, 1), bucketOf(hour))
        assertEquals(RelativeTimeBucket(RelativeTimeUnit.HOURS, 23), bucketOf(day - 1))
    }

    @Test
    fun `a day ends the hours`() {
        assertEquals(RelativeTimeBucket(RelativeTimeUnit.DAYS, 1), bucketOf(day))
        assertEquals(RelativeTimeBucket(RelativeTimeUnit.DAYS, 6), bucketOf(week - 1))
    }

    @Test
    fun `a week ends the days`() {
        assertEquals(RelativeTimeBucket(RelativeTimeUnit.WEEKS, 1), bucketOf(week))
        assertEquals(RelativeTimeBucket(RelativeTimeUnit.WEEKS, 4), bucketOf(29 * day))
        assertEquals(RelativeTimeBucket(RelativeTimeUnit.WEEKS, 4), bucketOf(month - 1))
    }

    @Test
    fun `thirty days ends the weeks`() {
        assertEquals(RelativeTimeBucket(RelativeTimeUnit.MONTHS, 1), bucketOf(30 * day))
        assertEquals(RelativeTimeBucket(RelativeTimeUnit.MONTHS, 12), bucketOf(364 * day))
        assertEquals(RelativeTimeBucket(RelativeTimeUnit.MONTHS, 12), bucketOf(year - 1))
    }

    @Test
    fun `a year ends the months`() {
        assertEquals(RelativeTimeBucket(RelativeTimeUnit.YEARS, 1), bucketOf(year))
        assertEquals(RelativeTimeBucket(RelativeTimeUnit.YEARS, 1), bucketOf(2 * year - 1))
    }

    /**
     * 2 and 5 are the two Polish plural classes that "%d lat temu" collapsed:
     * 2 needs "lata", 5 needs "lat". The bucket has to carry the number for the
     * resource layer to tell them apart at all.
     */
    @Test
    fun `the years bucket carries the real count`() {
        assertEquals(RelativeTimeBucket(RelativeTimeUnit.YEARS, 2), bucketOf(2 * year))
        assertEquals(RelativeTimeBucket(RelativeTimeUnit.YEARS, 3), bucketOf(3 * year))
        assertEquals(RelativeTimeBucket(RelativeTimeUnit.YEARS, 4), bucketOf(4 * year))
        assertEquals(RelativeTimeBucket(RelativeTimeUnit.YEARS, 5), bucketOf(5 * year))
        assertEquals(RelativeTimeBucket(RelativeTimeUnit.YEARS, 22), bucketOf(22 * year))
    }

    // --- the invariant the wording depends on --------------------------------

    @Test
    fun `a counted bucket never carries zero`() {
        // "0 minut temu" is not a thing any language renders well, and a zero
        // would land in the CLDR "other" class in Polish — the wrong form for
        // the wrong number. Walk every boundary and one second either side.
        val boundaries = listOf(minute, hour, day, week, month, year)
        val probes = boundaries.flatMap { listOf(it - 1, it, it + 1) } +
            listOf(0L, 45L, 90L, 5 * hour, 3 * day, 3 * week, 7 * month, 9 * year)

        probes.forEach { ago ->
            val bucket = bucketOf(ago)
            if (bucket.unit != RelativeTimeUnit.NEVER && bucket.unit != RelativeTimeUnit.JUST_NOW) {
                assertTrue(
                    "$ago s ago bucketed as ${bucket.unit} with count ${bucket.count}",
                    bucket.count >= 1L,
                )
            }
        }
    }

    @Test
    fun `the buckets march forward as the timestamp gets older`() {
        // Guards against a reordered when-branch: every threshold crossing must
        // move to a coarser unit, never back to a finer one.
        val order = listOf(
            RelativeTimeUnit.JUST_NOW,
            RelativeTimeUnit.MINUTES,
            RelativeTimeUnit.HOURS,
            RelativeTimeUnit.DAYS,
            RelativeTimeUnit.WEEKS,
            RelativeTimeUnit.MONTHS,
            RelativeTimeUnit.YEARS,
        )
        val samples = listOf(
            0L, 59L, minute, hour - 1, hour, day - 1, day, week - 1, week,
            month - 1, month, year - 1, year, 10 * year,
        )
        var lowest = 0
        samples.forEach { ago ->
            val index = order.indexOf(bucketOf(ago).unit)
            assertTrue("$ago s ago produced an unexpected unit", index >= 0)
            assertTrue("$ago s ago went back to a finer unit", index >= lowest)
            lowest = index
        }
    }
}
