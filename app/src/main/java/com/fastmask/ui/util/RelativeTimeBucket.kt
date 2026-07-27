package com.fastmask.ui.util

/**
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

    private const val MINUTE_SECONDS = 60L
    private const val HOUR_SECONDS = 3_600L
    private const val DAY_SECONDS = 86_400L
    private const val WEEK_SECONDS = DAY_SECONDS * 7
    private const val MONTH_SECONDS = DAY_SECONDS * 30
    private const val YEAR_SECONDS = DAY_SECONDS * 365

    /**
     * @param epochSecond the moment being described, or null when there is none.
     * @param nowSec the reference "now".
     */
    fun of(epochSecond: Long?, nowSec: Long): RelativeTimeBucket {
        if (epochSecond == null) return RelativeTimeBucket(RelativeTimeUnit.NEVER, 0L)

        // Device and server clocks can disagree; clamp a future instant to zero
        // so it renders as JUST_NOW instead of leaking a negative count.
        val diff = (nowSec - epochSecond).coerceAtLeast(0)

        return when {
            diff < MINUTE_SECONDS -> RelativeTimeBucket(RelativeTimeUnit.JUST_NOW, 0L)
            diff < HOUR_SECONDS -> RelativeTimeBucket(RelativeTimeUnit.MINUTES, diff / MINUTE_SECONDS)
            diff < DAY_SECONDS -> RelativeTimeBucket(RelativeTimeUnit.HOURS, diff / HOUR_SECONDS)
            diff < WEEK_SECONDS -> RelativeTimeBucket(RelativeTimeUnit.DAYS, diff / DAY_SECONDS)
            diff < MONTH_SECONDS -> RelativeTimeBucket(RelativeTimeUnit.WEEKS, diff / WEEK_SECONDS)
            diff < YEAR_SECONDS -> RelativeTimeBucket(RelativeTimeUnit.MONTHS, diff / MONTH_SECONDS)
            else -> RelativeTimeBucket(RelativeTimeUnit.YEARS, diff / YEAR_SECONDS)
        }
    }
}
