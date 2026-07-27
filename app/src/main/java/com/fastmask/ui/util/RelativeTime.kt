package com.fastmask.ui.util

import android.content.Context
import com.fastmask.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

object RelativeTime {

    // These six labels are compact card metadata, not prose. Fourteen shipped
    // locales already use abbreviations by design, and promoting one key to
    // plurals is all-or-nothing across all twenty locales. A locale left as a
    // string falls back to the default language, while one like Russian would
    // have to spell four identical plural items that all read the same. When a
    // form never varies, that is not grammar, it is noise.
    fun format(context: Context, instant: Instant?, nowSec: Long = Instant.now().epochSecond): String {
        val bucket = RelativeTimeBuckets.of(
            epochSecond = instant?.epochSecond,
            nowSec = nowSec,
        )

        return when (bucket.unit) {
            RelativeTimeUnit.NEVER -> context.getString(R.string.time_never)
            RelativeTimeUnit.JUST_NOW -> context.getString(R.string.time_just_now)
            RelativeTimeUnit.MINUTES -> context.getString(R.string.time_min_ago, bucket.count)
            RelativeTimeUnit.HOURS -> context.getString(R.string.time_hour_ago, bucket.count)
            RelativeTimeUnit.DAYS -> context.getString(R.string.time_day_ago, bucket.count)
            RelativeTimeUnit.WEEKS -> context.getString(R.string.time_week_ago, bucket.count)
            RelativeTimeUnit.MONTHS -> context.getString(R.string.time_month_ago, bucket.count)
            RelativeTimeUnit.YEARS -> context.getString(R.string.time_year_ago, bucket.count)
        }
    }

    private val fullFormatter = DateTimeFormatter
        .ofLocalizedDateTime(FormatStyle.MEDIUM)
        .withZone(ZoneId.systemDefault())

    fun full(instant: Instant?): String =
        instant?.let { fullFormatter.withLocale(Locale.getDefault()).format(it) } ?: "—"
}
