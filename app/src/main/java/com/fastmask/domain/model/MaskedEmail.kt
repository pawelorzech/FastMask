package com.fastmask.domain.model

import java.time.Instant

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
    val lastMessageAt: Instant?
) {
    val displayName: String
        get() = description?.takeIf { it.isNotBlank() }
            ?: forDomain?.takeIf { it.isNotBlank() }
            ?: email.substringBefore("@")

    val isActive: Boolean
        get() = state == EmailState.ENABLED || state == EmailState.PENDING
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
