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
    /**
     * Raw, because a SetResponse only has to return server-set properties: the
     * fields the request supplied may be absent and are merged back by JmapApi.
     * Decoding into [MaskedEmailDto] directly would turn an absent `state` into
     * the DTO's default (pending) and lose what the request asked for.
     */
    val created: Map<String, JsonObject>? = null,
    val updated: Map<String, JsonElement?>? = null,
    val destroyed: List<String>? = null,
    val notCreated: Map<String, JmapSetError>? = null,
    val notUpdated: Map<String, JmapSetError>? = null,
    val notDestroyed: Map<String, JmapSetError>? = null
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
