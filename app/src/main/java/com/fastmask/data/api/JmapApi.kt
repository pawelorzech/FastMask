package com.fastmask.data.api

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JmapApi @Inject constructor(
    private val jmapService: JmapService,
    private val json: Json
) {
    private var cachedSession: JmapSession? = null
    private var cachedAccountId: String? = null

    suspend fun getSession(token: String): Result<JmapSession> = runCatching {
        val authHeader = "Bearer $token"
        jmapService.getSession(authHeader = authHeader).also {
            cachedSession = it
            cachedAccountId = it.primaryAccounts["https://www.fastmail.com/dev/maskedemail"]
                ?: it.primaryAccounts.values.firstOrNull()
        }
    }

    fun getAccountId(): String? = cachedAccountId

    fun getApiUrl(): String = cachedSession?.apiUrl ?: JmapService.FASTMAIL_API_URL

    suspend fun getMaskedEmails(token: String): Result<List<MaskedEmailDto>> = runCatching {
        val accountId = cachedAccountId ?: throw IllegalStateException("Session not initialized")
        val authHeader = "Bearer $token"

        val methodCall = buildJsonArray {
            add(JsonPrimitive("MaskedEmail/get"))
            add(buildJsonObject {
                put("accountId", JsonPrimitive(accountId))
            })
            add(JsonPrimitive("0"))
        }

        val request = JmapRequest(
            using = JmapService.JMAP_CAPABILITIES,
            methodCalls = listOf(methodCall)
        )

        val response = jmapService.executeMethod(
            url = getApiUrl(),
            authHeader = authHeader,
            request = request
        )

        parseGetResponse(response)
    }

    suspend fun createMaskedEmail(
        token: String,
        create: MaskedEmailCreate
    ): Result<MaskedEmailDto> = runCatching {
        val accountId = cachedAccountId ?: throw IllegalStateException("Session not initialized")
        val authHeader = "Bearer $token"

        val createObject = buildJsonObject {
            put("state", JsonPrimitive(create.state.name.lowercase()))
            create.forDomain?.let { put("forDomain", JsonPrimitive(it)) }
            create.description?.let { put("description", JsonPrimitive(it)) }
            create.emailPrefix?.let { put("emailPrefix", JsonPrimitive(it)) }
            create.url?.let { put("url", JsonPrimitive(it)) }
        }

        val methodCall = buildJsonArray {
            add(JsonPrimitive("MaskedEmail/set"))
            add(buildJsonObject {
                put("accountId", JsonPrimitive(accountId))
                put("create", buildJsonObject {
                    put("new1", createObject)
                })
            })
            add(JsonPrimitive("0"))
        }

        val request = JmapRequest(
            using = JmapService.JMAP_CAPABILITIES,
            methodCalls = listOf(methodCall)
        )

        val response = jmapService.executeMethod(
            url = getApiUrl(),
            authHeader = authHeader,
            request = request
        )

        parseSetResponseCreated(response, "new1")
    }

    suspend fun updateMaskedEmail(
        token: String,
        id: String,
        update: MaskedEmailUpdate
    ): Result<Unit> = runCatching {
        val accountId = cachedAccountId ?: throw IllegalStateException("Session not initialized")
        val authHeader = "Bearer $token"

        val updateObject = buildJsonObject {
            update.state?.let { put("state", JsonPrimitive(it.name.lowercase())) }
            update.forDomain?.let { put("forDomain", JsonPrimitive(it)) }
            update.description?.let { put("description", JsonPrimitive(it)) }
            update.url?.let { put("url", JsonPrimitive(it)) }
        }

        val methodCall = buildJsonArray {
            add(JsonPrimitive("MaskedEmail/set"))
            add(buildJsonObject {
                put("accountId", JsonPrimitive(accountId))
                put("update", buildJsonObject {
                    put(id, updateObject)
                })
            })
            add(JsonPrimitive("0"))
        }

        val request = JmapRequest(
            using = JmapService.JMAP_CAPABILITIES,
            methodCalls = listOf(methodCall)
        )

        val response = jmapService.executeMethod(
            url = getApiUrl(),
            authHeader = authHeader,
            request = request
        )

        parseSetResponseUpdated(response, id)
    }

    suspend fun deleteMaskedEmail(token: String, id: String): Result<Unit> = runCatching {
        val accountId = cachedAccountId ?: throw IllegalStateException("Session not initialized")
        val authHeader = "Bearer $token"

        val methodCall = buildJsonArray {
            add(JsonPrimitive("MaskedEmail/set"))
            add(buildJsonObject {
                put("accountId", JsonPrimitive(accountId))
                put("destroy", buildJsonArray { add(JsonPrimitive(id)) })
            })
            add(JsonPrimitive("0"))
        }

        val request = JmapRequest(
            using = JmapService.JMAP_CAPABILITIES,
            methodCalls = listOf(methodCall)
        )

        val response = jmapService.executeMethod(
            url = getApiUrl(),
            authHeader = authHeader,
            request = request
        )

        parseSetResponseDestroyed(response, id)
    }

    private fun parseGetResponse(response: JmapResponse): List<MaskedEmailDto> {
        val methodResponse = response.methodResponses.firstOrNull()
            ?: throw IllegalStateException("Empty response")

        val responseType = (methodResponse[0] as? JsonPrimitive)?.content
        if (responseType == "error") {
            val errorData = methodResponse[1] as? JsonObject
            val errorType = (errorData?.get("type") as? JsonPrimitive)?.content
            throw JmapException("JMAP Error: $errorType")
        }

        val data = methodResponse[1] as? JsonObject
            ?: throw IllegalStateException("Invalid response format")

        val getResponse: MaskedEmailGetResponse = json.decodeFromJsonElement(data)
        return getResponse.list
    }

    private fun parseSetResponseCreated(response: JmapResponse, createId: String): MaskedEmailDto {
        val methodResponse = response.methodResponses.firstOrNull()
            ?: throw IllegalStateException("Empty response")

        val responseType = (methodResponse[0] as? JsonPrimitive)?.content
        if (responseType == "error") {
            val errorData = methodResponse[1] as? JsonObject
            val errorType = (errorData?.get("type") as? JsonPrimitive)?.content
            throw JmapException("JMAP Error: $errorType")
        }

        val data = methodResponse[1] as? JsonObject
            ?: throw IllegalStateException("Invalid response format")

        val setResponse: MaskedEmailSetResponse = json.decodeFromJsonElement(data)

        setResponse.notCreated?.get(createId)?.let { error ->
            throw JmapException("Failed to create: ${error.type} - ${error.description}")
        }

        return setResponse.created?.get(createId)
            ?: throw IllegalStateException("Created email not found in response")
    }

    private fun parseSetResponseUpdated(response: JmapResponse, id: String) {
        val methodResponse = response.methodResponses.firstOrNull()
            ?: throw IllegalStateException("Empty response")

        val responseType = (methodResponse[0] as? JsonPrimitive)?.content
        if (responseType == "error") {
            val errorData = methodResponse[1] as? JsonObject
            val errorType = (errorData?.get("type") as? JsonPrimitive)?.content
            throw JmapException("JMAP Error: $errorType")
        }

        val data = methodResponse[1] as? JsonObject
            ?: throw IllegalStateException("Invalid response format")

        val setResponse: MaskedEmailSetResponse = json.decodeFromJsonElement(data)

        setResponse.notUpdated?.get(id)?.let { error ->
            throw JmapException("Failed to update: ${error.type} - ${error.description}")
        }
    }

    private fun parseSetResponseDestroyed(response: JmapResponse, id: String) {
        val methodResponse = response.methodResponses.firstOrNull()
            ?: throw IllegalStateException("Empty response")

        val responseType = (methodResponse[0] as? JsonPrimitive)?.content
        if (responseType == "error") {
            val errorData = methodResponse[1] as? JsonObject
            val errorType = (errorData?.get("type") as? JsonPrimitive)?.content
            throw JmapException("JMAP Error: $errorType")
        }

        val data = methodResponse[1] as? JsonObject
            ?: throw IllegalStateException("Invalid response format")

        val setResponse: MaskedEmailSetResponse = json.decodeFromJsonElement(data)

        setResponse.notDestroyed?.get(id)?.let { error ->
            throw JmapException("Failed to delete: ${error.type} - ${error.description}")
        }
    }

    fun clearSession() {
        cachedSession = null
        cachedAccountId = null
    }
}

class JmapException(message: String) : Exception(message)
