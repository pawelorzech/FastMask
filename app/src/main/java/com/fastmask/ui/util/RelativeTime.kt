package com.fastmask.ui.util

import android.icu.text.DisplayContext
import android.icu.text.RelativeDateTimeFormatter
import android.icu.text.RelativeDateTimeFormatter.RelativeUnit
import android.icu.util.ULocale

import android.content.Context
import com.fastmask.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

object RelativeTime {

    /**
     * "N units ago" comes from the platform's ICU [RelativeDateTimeFormatter] (short
     * style) in the app's current locale, not from string resources.
     *
     * ICU carries CLDR's reviewed short forms and plural rules for every locale the
     * app supports ("vor 5 Mon.", "il y a 5 mois", "5 мес. назад", "5 か月前"). Only
     * "just now" and "never" stay as resources.
     */
    fun format(context: Context, instant: Instant?, nowSec: Long = Instant.now().epochSecond): String {
        val bucket = RelativeTimeBuckets.of(
            epochSecond = instant?.epochSecond,
            nowSec = nowSec,
        )
        return format(context, bucket)
    }

    fun format(context: Context, bucket: RelativeTimeBucket): String {
        // Buckets cap well inside Int range (minutes < 60, hours < 24, ...);
        // only YEARS is open-ended, and it counts whole years since 1970.
        val count = bucket.count.toInt()

        return when (bucket.unit) {
            RelativeTimeUnit.NEVER -> context.getString(R.string.time_never)
            RelativeTimeUnit.JUST_NOW -> context.getString(R.string.time_just_now)
            RelativeTimeUnit.MINUTES -> context.ago(count, RelativeUnit.MINUTES)
            RelativeTimeUnit.HOURS -> context.ago(count, RelativeUnit.HOURS)
            RelativeTimeUnit.DAYS -> context.ago(count, RelativeUnit.DAYS)
            RelativeTimeUnit.WEEKS -> context.ago(count, RelativeUnit.WEEKS)
            RelativeTimeUnit.MONTHS -> context.ago(count, RelativeUnit.MONTHS)
            RelativeTimeUnit.YEARS -> context.ago(count, RelativeUnit.YEARS)
        }
    }

    @Volatile
    private var cachedFormatter: Pair<Locale, RelativeDateTimeFormatter>? = null

    /** Short "N [unit] ago" in the locale the app is currently displayed in. */
    private fun Context.ago(count: Int, unit: RelativeUnit): String {
        val locale = resources.configuration.locales[0]
        val formatter = cachedFormatter?.takeIf { it.first == locale }?.second
            ?: RelativeDateTimeFormatter.getInstance(
                ULocale.forLocale(locale),
                null,
                RelativeDateTimeFormatter.Style.SHORT,
                DisplayContext.CAPITALIZATION_NONE,
            ).also { cachedFormatter = locale to it }
        return formatter.format(count.toDouble(), RelativeDateTimeFormatter.Direction.LAST, unit)
    }

    private val fullFormatter = DateTimeFormatter
        .ofLocalizedDateTime(FormatStyle.MEDIUM)
        .withZone(ZoneId.systemDefault())

    fun full(instant: Instant): String =
        fullFormatter.withLocale(Locale.getDefault()).format(instant)
}
