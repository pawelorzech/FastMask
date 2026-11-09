package com.fastmask.ui.util

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

data class RelativeTimeBucket(val unit: RelativeTimeUnit, val count: Long)

object RelativeTimeBuckets {

    private const val MINUTE_SECONDS = 60L
    private const val HOUR_SECONDS = 3_600L
    private const val DAY_SECONDS = 86_400L
    private const val WEEK_SECONDS = DAY_SECONDS * 7
    private const val MONTH_SECONDS = DAY_SECONDS * 30
    private const val YEAR_SECONDS = DAY_SECONDS * 365

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
