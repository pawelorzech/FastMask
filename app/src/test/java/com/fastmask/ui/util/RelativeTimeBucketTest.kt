package com.fastmask.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The bucketing half of relative times, pinned as a pure function.
 *
 * A wording baked into one `getString(..., diff / YEAR)` expression read "3 lat
 * temu" in Polish, because only the language's plural rules know that count
 * takes a different form. So [RelativeTimeBuckets.of] returns the COUNT and the
 * unit as values, and `RelativeTime.format` (the Context-facing entry point,
 * built on it) turns them into words.
 *
 * These tests pin the thresholds: every boundary below governs what a user sees.
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
}
