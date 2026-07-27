package com.fastmask.ui.util

import android.content.Context
import androidx.annotation.PluralsRes
import com.fastmask.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

object RelativeTime {

    /**
     * The six "N units ago" labels are `<plurals>`, not `<string>`, and are read
     * with [android.content.res.Resources.getQuantityString].
     *
     * `getString(id, count)` substitutes a number into one fixed sentence. That
     * is only safe when the unit noun does not change shape with the count —
     * true for the English abbreviations the resources were written as ("%dm
     * ago"), false the moment a translator spells the noun out. Polish did, and
     * shipped `%d lat temu`: the genitive plural, right for 5 and above, wrong
     * for the 1 to 4 a mask card shows most of the time ("3 lat temu").
     *
     * Most locales here still read as one invariant abbreviation, so their
     * plural items are identical by design — the categories exist so a
     * translator has a slot to differ in, not because every language does.
     * The resource type has to be the same in all twenty locales anyway: a
     * `<string>` left behind would resolve against the default language, and an
     * Arabic user would get English.
     */
    fun format(context: Context, instant: Instant?, nowSec: Long = Instant.now().epochSecond): String {
        val bucket = RelativeTimeBuckets.of(
            epochSecond = instant?.epochSecond,
            nowSec = nowSec,
        )

        // Buckets cap well inside Int range (minutes < 60, hours < 24, ...);
        // only YEARS is open-ended, and it counts whole years since 1970.
        val count = bucket.count.toInt()

        return when (bucket.unit) {
            RelativeTimeUnit.NEVER -> context.getString(R.string.time_never)
            RelativeTimeUnit.JUST_NOW -> context.getString(R.string.time_just_now)
            RelativeTimeUnit.MINUTES -> context.quantity(R.plurals.time_min_ago, count)
            RelativeTimeUnit.HOURS -> context.quantity(R.plurals.time_hour_ago, count)
            RelativeTimeUnit.DAYS -> context.quantity(R.plurals.time_day_ago, count)
            RelativeTimeUnit.WEEKS -> context.quantity(R.plurals.time_week_ago, count)
            RelativeTimeUnit.MONTHS -> context.quantity(R.plurals.time_month_ago, count)
            RelativeTimeUnit.YEARS -> context.quantity(R.plurals.time_year_ago, count)
        }
    }

    /** [count] is both the category selector and the number the label prints. */
    private fun Context.quantity(@PluralsRes id: Int, count: Int): String =
        resources.getQuantityString(id, count, count)

    private val fullFormatter = DateTimeFormatter
        .ofLocalizedDateTime(FormatStyle.MEDIUM)
        .withZone(ZoneId.systemDefault())

    fun full(instant: Instant?): String =
        instant?.let { fullFormatter.withLocale(Locale.getDefault()).format(it) } ?: "—"
}
