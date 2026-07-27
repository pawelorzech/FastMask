package com.fastmask.ui.util

/**
 * STUB — written by the test author, to be implemented.
 *
 * The unit a relative timestamp is rendered in. Split out of [RelativeTime] so
 * the bucketing thresholds are testable without a `Context`, and — the reason
 * this exists at all — so the caller can hand the COUNT to the resource layer.
 * A language with more than one plural form cannot be served by
 * `getString(id, count)`; it needs `getQuantityString(id, count, count)`, and
 * that call needs the count separated from the resource choice.
 */
enum class RelativeTimeUnit {
    NEVER,
    JUST_NOW,
    MINUTES,
    HOURS,
    DAYS,
    WEEKS,
    MONTHS,
    YEARS,
}

/**
 * A bucketed timestamp: which [unit] to render in, and how many of them.
 *
 * [count] is 0 for [RelativeTimeUnit.NEVER] and [RelativeTimeUnit.JUST_NOW],
 * which take no number, and at least 1 for every counted unit.
 */
data class RelativeTimeBucket(val unit: RelativeTimeUnit, val count: Long)

object RelativeTimeBuckets {

    /**
     * STUB: returns a placeholder so the test suite compiles and fails.
     *
     * @param epochSecond the moment being described, or null when there is none.
     * @param nowSec the reference "now".
     */
    @Suppress("UNUSED_PARAMETER")
    fun of(epochSecond: Long?, nowSec: Long): RelativeTimeBucket =
        RelativeTimeBucket(RelativeTimeUnit.NEVER, 0L)
}
