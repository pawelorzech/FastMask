package com.fastmask.domain.model

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

data class MaskedEmail(
    val id: String,
    val email: String,
    val state: EmailState,
    val forDomain: String?,
    val description: String?,
    val createdBy: String?,
    val url: String?,
    val emailPrefix: String?,
    val createdAt: Instant?,
    val lastMessageAt: Instant?,
    val formattedCreatedAt: String? = createdAt?.let { formatInstant(it) },
    val formattedLastMessageAt: String? = lastMessageAt?.let { formatInstant(it) }
) {
    val displayName: String
        get() = description?.takeIf { it.isNotBlank() }
            ?: forDomain?.takeIf { it.isNotBlank() }
            ?: email.substringBefore("@")

    val isActive: Boolean
        get() = state == EmailState.ENABLED || state == EmailState.PENDING

    companion object {
        private val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
            .withZone(ZoneId.systemDefault())

        fun formatInstant(instant: Instant): String = formatter.format(instant)
    }
}

enum class EmailState {
    PENDING,
    ENABLED,
    DISABLED,
    DELETED
}

data class CreateMaskedEmailParams(
    val state: EmailState = EmailState.ENABLED,
    val forDomain: String? = null,
    val description: String? = null,
    val emailPrefix: String? = null,
    val url: String? = null
)

data class UpdateMaskedEmailParams(
    val state: EmailState? = null,
    val forDomain: String? = null,
    val description: String? = null,
    val url: String? = null
)
