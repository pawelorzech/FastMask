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

    /** When the mask last did anything: its last message, else its creation. Drives sort, sections and the row label. */
    val lastActivityAt: Instant?
        get() = lastMessageAt ?: createdAt

    /** The part of [email] before the "@". */
    val localPart: String
        get() = email.substringBefore('@')

    /** The part of [email] after the "@", empty when there is none. */
    val domainPart: String
        get() = email.substringAfter('@', missingDelimiterValue = "")

    /** [forDomain] when it names a site, else null (blank counts as unset). */
    val siteDomain: String?
        get() = forDomain?.takeIf { it.isNotBlank() }

    /** What the avatar letter and shape are derived from: the site, else the address. */
    val avatarSeed: String
        get() = siteDomain ?: email
}

/**
 * A mask's lifecycle state. The `@SerialName`s are Fastmail's JMAP wire
 * values and the single source of truth for them: [wire] reads them back, so
 * no call site derives a wire name by hand.
 */
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

    /** The JMAP value for this state, e.g. `"enabled"`. */
    val wire: String
        get() = EmailState.serializer().descriptor.getElementName(ordinal)

    /** ENABLED and PENDING both count as "Active". */
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

    /** The URL to set, or null when there is nothing to set (absent or [clearsUrl]). */
    val urlToSet: String? get() = url?.takeIf { it.isNotEmpty() }
}
