package com.fastmask.data.api

import com.fastmask.domain.model.EmailState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class JmapSession(
    val apiUrl: String,
    val primaryAccounts: Map<String, String>,
    val accounts: Map<String, JmapAccount>,
)

@Serializable
data class JmapAccount(
    val accountCapabilities: JsonObject = JsonObject(emptyMap())
)

@Serializable
data class JmapRequest(
    val using: List<String>,
    val methodCalls: List<JsonArray>
)

@Serializable
data class JmapResponse(
    val methodResponses: List<JsonArray>,
)

@Serializable
data class MaskedEmailGetResponse(
    val list: List<MaskedEmailDto>,
)

@Serializable
data class MaskedEmailSetResponse(
    val created: Map<String, MaskedEmailCreated>? = null,
    val updated: Map<String, JsonElement?>? = null,
    val destroyed: List<String>? = null,
    val notCreated: Map<String, JmapSetError>? = null,
    val notUpdated: Map<String, JmapSetError>? = null,
    val notDestroyed: Map<String, JmapSetError>? = null
)

/**
 * JMAP SetResponse only has to return server-set/defaulted properties. Fields
 * supplied in the create request may therefore be absent and are merged back
 * by JmapApi instead of being guessed by serializer defaults.
 */
@Serializable
data class MaskedEmailCreated(
    val id: String,
    val email: String,
    val state: EmailState? = null,
    val forDomain: String? = null,
    val description: String? = null,
    val createdBy: String? = null,
    val url: String? = null,
    val createdAt: String? = null,
    val lastMessageAt: String? = null,
)

@Serializable
data class JmapSetError(
    val type: String,
    val description: String? = null
)

@Serializable
data class MaskedEmailDto(
    val id: String,
    val email: String,
    // Fastmail's documented default is pending. A response should normally
    // include the property, but a defensive default must preserve API semantics.
    val state: EmailState = EmailState.PENDING,
    val forDomain: String? = null,
    val description: String? = null,
    val createdBy: String? = null,
    val url: String? = null,
    val emailPrefix: String? = null,
    val createdAt: String? = null,
    val lastMessageAt: String? = null
)

data class MaskedEmailUpdate(
    val state: EmailState? = null,
    val forDomain: String? = null,
    val description: String? = null,
    val url: String? = null,
    /** True means send JSON null; a null [url] alone means omit the patch. */
    val clearUrl: Boolean = false,
)
