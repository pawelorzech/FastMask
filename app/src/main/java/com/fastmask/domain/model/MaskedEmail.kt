package com.fastmask.domain.model

import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
    /** Counts as "Active" in the list: a fresh mask is PENDING until its first message. */
    val isActive: Boolean
        get() = state.isActive

    val lastActivityAt: Instant?
        get() = lastMessageAt ?: createdAt

    val localPart: String
        get() = email.substringBefore('@')

    val domainPart: String
        get() = email.substringAfter('@', missingDelimiterValue = "")

    val siteDomain: String?
        get() = forDomain?.takeIf { it.isNotBlank() }

    val avatarSeed: String
        get() = siteDomain ?: email
}

/** The `@SerialName`s are Fastmail's JMAP wire values. */
@Serializable
enum class EmailState {
    @SerialName("pending")
    PENDING,
    @SerialName("enabled")
    ENABLED,
    @SerialName("disabled")
    DISABLED,
    @SerialName("deleted")
    DELETED;

    val wire: String
        get() = EmailState.serializer().descriptor.getElementName(ordinal)

    val isActive: Boolean
        get() = this == ENABLED || this == PENDING
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
) {
    /** An empty [url] means "remove it"; a null one means "leave it alone". */
    val clearsUrl: Boolean get() = url != null && url.isEmpty()

    val urlToSet: String? get() = url?.takeIf { it.isNotEmpty() }
}
