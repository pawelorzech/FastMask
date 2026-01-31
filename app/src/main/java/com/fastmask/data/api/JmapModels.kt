package com.fastmask.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class JmapSession(
    val username: String,
    val apiUrl: String,
    val primaryAccounts: Map<String, String>,
    val accounts: Map<String, JmapAccount>,
    val capabilities: JsonObject,
    val state: String
)

@Serializable
data class JmapAccount(
    val name: String,
    val isPersonal: Boolean = true,
    val isReadOnly: Boolean = false,
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
    val sessionState: String? = null
)

@Serializable
data class MaskedEmailGetResponse(
    val accountId: String,
    val state: String,
    val list: List<MaskedEmailDto>,
    val notFound: List<String> = emptyList()
)

@Serializable
data class MaskedEmailSetResponse(
    val accountId: String,
    val oldState: String? = null,
    val newState: String? = null,
    val created: Map<String, MaskedEmailDto>? = null,
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
    val state: MaskedEmailState = MaskedEmailState.ENABLED,
    val forDomain: String? = null,
    val description: String? = null,
    val createdBy: String? = null,
    val url: String? = null,
    val emailPrefix: String? = null,
    val createdAt: String? = null,
    val lastMessageAt: String? = null
)

@Serializable
enum class MaskedEmailState {
    @SerialName("pending")
    PENDING,
    @SerialName("enabled")
    ENABLED,
    @SerialName("disabled")
    DISABLED,
    @SerialName("deleted")
    DELETED
}

@Serializable
data class MaskedEmailCreate(
    val state: MaskedEmailState = MaskedEmailState.ENABLED,
    val forDomain: String? = null,
    val description: String? = null,
    val emailPrefix: String? = null,
    val url: String? = null
)

@Serializable
data class MaskedEmailUpdate(
    val state: MaskedEmailState? = null,
    val forDomain: String? = null,
    val description: String? = null,
    val url: String? = null
)
