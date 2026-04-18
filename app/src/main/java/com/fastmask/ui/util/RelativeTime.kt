package com.fastmask.ui.util

import android.content.Context
import com.fastmask.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

object RelativeTime {

    fun format(context: Context, instant: Instant?, nowSec: Long = Instant.now().epochSecond): String {
        if (instant == null) return context.getString(R.string.time_never)
        val thenSec = instant.epochSecond
        val diff = (nowSec - thenSec).coerceAtLeast(0)

        return when {
            diff < 60L -> context.getString(R.string.time_just_now)
            diff < 3_600L -> context.getString(R.string.time_min_ago, diff / 60)
            diff < 86_400L -> context.getString(R.string.time_hour_ago, diff / 3_600)
            diff < 86_400L * 7 -> context.getString(R.string.time_day_ago, diff / 86_400)
            diff < 86_400L * 30 -> context.getString(R.string.time_week_ago, diff / (86_400L * 7))
            diff < 86_400L * 365 -> context.getString(R.string.time_month_ago, diff / (86_400L * 30))
            else -> context.getString(R.string.time_year_ago, diff / (86_400L * 365))
        }
    }

    private val fullFormatter = DateTimeFormatter
        .ofLocalizedDateTime(FormatStyle.MEDIUM)
        .withZone(ZoneId.systemDefault())

    fun full(instant: Instant?): String =
        instant?.let { fullFormatter.withLocale(Locale.getDefault()).format(it) } ?: "—"
}
