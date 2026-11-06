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
    val isActive: Boolean
        get() = state == EmailState.ENABLED || state == EmailState.PENDING
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
