package com.fastmask.ui.list

import androidx.annotation.StringRes
import com.fastmask.R
import com.fastmask.domain.model.EmailState
import com.fastmask.domain.model.MaskedEmail

enum class EmailFilter(
    @StringRes val labelRes: Int,
    private val admits: (MaskedEmail) -> Boolean,
) {
    ALL(R.string.filter_all, { true }),
    ENABLED(R.string.filter_enabled, { it.isActive }),
    DISABLED(R.string.filter_disabled, { it.state == EmailState.DISABLED }),
    DELETED(R.string.filter_deleted, { it.state == EmailState.DELETED });

    fun matches(email: MaskedEmail): Boolean = admits(email)

    companion object {
        fun countsOf(emails: List<MaskedEmail>): Map<EmailFilter, Int> =
            entries.associateWith { filter -> emails.count(filter::matches) }
    }
}

fun filterMasks(emails: List<MaskedEmail>, query: String, filter: EmailFilter): List<MaskedEmail> {
    val hasQuery = query.isNotBlank()
    if (filter == EmailFilter.ALL && !hasQuery) return emails
    return emails.filter { email ->
        filter.matches(email) && (!hasQuery ||
            email.email.contains(query, ignoreCase = true) ||
            email.description?.contains(query, ignoreCase = true) == true ||
            email.forDomain?.contains(query, ignoreCase = true) == true ||
            email.url?.contains(query, ignoreCase = true) == true)
    }
}
