package com.fastmask.ui.list

import androidx.annotation.StringRes
import com.fastmask.R
import com.fastmask.domain.model.MaskedEmail
import com.fastmask.ui.util.RelativeTimeBuckets
import com.fastmask.ui.util.RelativeTimeUnit

/**
 * The time-bucket headers the mask list groups its rows under. Derived from the
 * same [RelativeTimeBuckets] the row's "3d" label uses, so a row can never sit
 * under "Today" while its label says "2d".
 */
internal enum class MaskTimeSection(@StringRes val titleRes: Int) {
    TODAY(R.string.list_section_today),
    THIS_WEEK(R.string.list_section_this_week),
    THIS_MONTH(R.string.list_section_this_month),
    EARLIER(R.string.list_section_earlier),
}

/** One header plus the rows below it, in the order the list already had them. */
internal data class MaskSection(val section: MaskTimeSection, val items: List<MaskedEmail>)

/** Which section a mask belongs to, by its last message (or creation) time. */
internal fun maskTimeSection(email: MaskedEmail, nowSec: Long): MaskTimeSection {
    val moment = (email.lastMessageAt ?: email.createdAt)?.epochSecond
    return when (RelativeTimeBuckets.of(moment, nowSec).unit) {
        RelativeTimeUnit.JUST_NOW,
        RelativeTimeUnit.MINUTES,
        RelativeTimeUnit.HOURS -> MaskTimeSection.TODAY
        RelativeTimeUnit.DAYS -> MaskTimeSection.THIS_WEEK
        RelativeTimeUnit.WEEKS -> MaskTimeSection.THIS_MONTH
        RelativeTimeUnit.MONTHS,
        RelativeTimeUnit.YEARS,
        RelativeTimeUnit.NEVER -> MaskTimeSection.EARLIER
    }
}

/** Groups [emails] into non-empty sections, newest section first; row order is preserved. */
internal fun groupMasksBySection(emails: List<MaskedEmail>, nowSec: Long): List<MaskSection> =
    emails.groupBy { maskTimeSection(it, nowSec) }
        .toSortedMap()
        .map { (section, items) -> MaskSection(section, items) }
