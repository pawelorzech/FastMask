package com.fastmask.data.api

import com.fastmask.core.runSuspendCatching
import com.fastmask.domain.auth.MaskedEmailScopeMissingException
import com.fastmask.domain.error.AuthRejectedException
import com.fastmask.domain.error.RateLimitedException
import com.fastmask.domain.error.ServerErrorException
import com.fastmask.domain.model.CreateMaskedEmailParams
import com.fastmask.domain.model.UpdateMaskedEmailParams
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JmapApi @Inject constructor(
    private val jmapService: JmapService,
    private val json: Json
) {
    /**
     * Everything one successful session fetch produced, as one immutable value:
     * the token it was fetched for, the masked-email account id and the session
     * itself (whose `apiUrl` has passed the trust check).
     */
    private class CachedSession(
        val token: String,
        val accountId: String,
        val session: JmapSession,
    ) {
        val apiUrl: String get() = session.apiUrl
    }

    // @Volatile: the lock-free fast path in ensureSession() runs on different
    // Dispatchers.IO threads than the writers. One field holding one immutable
    // value means a reader sees a whole session or none, never a torn mix of
    // account id, URL and token from two different fetches.
    @Volatile
    private var cached: CachedSession? = null
    private val sessionMutex = Mutex()

    private fun bearer(token: String) = "Bearer $token"

    /**
     * [runSuspendCatching] that also translates transport failures into the domain's own
     * types, so callers above the data layer never see Retrofit's `HttpException`.
     */
    private suspend inline fun <T> remote(block: suspend () -> T): Result<T> =
        runSuspendCatching(block).fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(it.asDomainFailure()) },
        )

    /** Fetches, validates and caches the session; also what sign-in uses to validate a token. */
    suspend fun getSession(token: String): Result<JmapSession> = remote { loadSession(token).session }

    private suspend fun loadSession(token: String): CachedSession {
        val session = jmapService.getSession(FastmailEndpoints.SESSION_URL, bearer(token))
        require(FastmailEndpoints.isTrustedHttpsUrl(session.apiUrl)) {
            "Untrusted apiUrl in session response"
        }
        val accountId = MaskedEmailScope.accountId(session)
            ?: throw MaskedEmailScopeMissingException()
        return CachedSession(token, accountId, session).also { cached = it }
    }

    private suspend fun ensureSession(token: String): CachedSession {
        cached?.takeIf { it.token == token }?.let { return it }
        sessionMutex.withLock {
            cached?.takeIf { it.token == token }?.let { return it }
            return loadSession(token)
        }
    }

    suspend fun getMaskedEmails(token: String): Result<List<MaskedEmailDto>> = remote {
        fetchMaskedEmails(token, ids = null)
    }

    /**
     * One mask by id (`MaskedEmail/get` restricted with `ids`). Returns null
     * when the server does not know the id, so callers can distinguish
     * "absent" from "network failed".
     */
    suspend fun getMaskedEmail(token: String, id: String): Result<MaskedEmailDto?> =
        remote {
            fetchMaskedEmails(token, ids = listOf(id)).firstOrNull()
        }

    private suspend fun fetchMaskedEmails(token: String, ids: List<String>?): List<MaskedEmailDto> =
        call<MaskedEmailGetResponse>(token, "MaskedEmail/get") {
            ids?.let { idList ->
                put("ids", buildJsonArray { idList.forEach { add(JsonPrimitive(it)) } })
            }
        }.list

    suspend fun createMaskedEmail(
        token: String,
        create: CreateMaskedEmailParams
    ): Result<MaskedEmailDto> = remote {
        val createObject = buildJsonObject {
            put("state", JsonPrimitive(create.state.wire))
            create.forDomain?.let { put("forDomain", JsonPrimitive(it)) }
            // Fastmail requires an empty string when the user supplied no
            // description; omitting it leaves interoperability to server defaults.
            put("description", JsonPrimitive(create.description.orEmpty()))
            create.emailPrefix?.let { put("emailPrefix", JsonPrimitive(it)) }
            create.url?.let { put("url", JsonPrimitive(it)) }
        }

        val response = call<MaskedEmailSetResponse>(token, "MaskedEmail/set") {
            put("create", buildJsonObject { put(CREATE_ID, createObject) })
        }

        response.notCreated?.get(CREATE_ID)?.let { error ->
            throw jmapSetException("Failed to create", error)
        }

        // A SetResponse only has to return server-set properties, so what the
        // request supplied may be absent (or an explicit null) and is merged back
        // from the request. `state` has a default on the DTO, so its absence has
        // to be seen on the raw entry: the default would be "pending", not what
        // was asked for.
        val entry = response.created?.get(CREATE_ID)
            ?: throw IllegalStateException("Created email not found in response")
        val created = JsonObject(entry.filterValues { it !is JsonNull })
        val dto = json.decodeFromJsonElement<MaskedEmailDto>(created)
        dto.copy(
            state = if ("state" in created) dto.state else create.state,
            forDomain = dto.forDomain ?: create.forDomain,
            description = dto.description ?: create.description.orEmpty(),
            url = dto.url ?: create.url,
        )
    }

    suspend fun updateMaskedEmail(
        token: String,
        id: String,
        params: UpdateMaskedEmailParams
    ): Result<Unit> = remote {
        val updateObject = buildJsonObject {
            params.state?.let { put("state", JsonPrimitive(it.wire)) }
            params.forDomain?.let { put("forDomain", JsonPrimitive(it)) }
            params.description?.let { put("description", JsonPrimitive(it)) }
            if (params.clearsUrl) {
                put("url", JsonNull)
            } else {
                params.urlToSet?.let { put("url", JsonPrimitive(it)) }
            }
        }

        val response = call<MaskedEmailSetResponse>(token, "MaskedEmail/set") {
            put("update", buildJsonObject { put(id, updateObject) })
        }

        response.notUpdated?.get(id)?.let { error ->
            throw jmapSetException("Failed to update", error)
        }

        if (response.updated?.containsKey(id) != true) {
            throw JmapException("Update not confirmed by server - ID not found in updated response")
        }
    }

    suspend fun deleteMaskedEmail(token: String, id: String): Result<Unit> = remote {
        val response = call<MaskedEmailSetResponse>(token, "MaskedEmail/set") {
            put("destroy", buildJsonArray { add(JsonPrimitive(id)) })
        }

        response.notDestroyed?.get(id)?.let { error ->
            throw jmapSetException("Failed to delete", error)
        }

        // A silent server no-op must not be reported as a successful destroy.
        if (response.destroyed?.contains(id) != true) {
            throw JmapException("Delete not confirmed by server - ID not found in destroyed response")
        }
    }

    /**
     * One JMAP method call: makes sure there is a session for [token], sends
     * the request envelope with the Bearer header, unwraps the single method
     * response (turning a JMAP `error` into [JmapException]) and decodes it as
     * [T]. [args] fills the method's arguments; `accountId` is added first.
     */
    private suspend inline fun <reified T> call(
        token: String,
        method: String,
        noinline args: JsonObjectBuilder.() -> Unit,
    ): T = json.decodeFromJsonElement(callRaw(token, method, args))

    private suspend fun callRaw(
        token: String,
        method: String,
        args: JsonObjectBuilder.() -> Unit,
    ): JsonObject {
        val session = ensureSession(token)

        val methodCall = buildJsonArray {
            add(JsonPrimitive(method))
            add(buildJsonObject {
                put("accountId", JsonPrimitive(session.accountId))
                args()
            })
            add(JsonPrimitive("0"))
        }

        val response = jmapService.executeMethod(
            url = session.apiUrl,
            authHeader = bearer(token),
            request = JmapRequest(
                using = FastmailEndpoints.CAPABILITIES,
                methodCalls = listOf(methodCall),
            ),
        )

        val methodResponse = response.methodResponses.firstOrNull()
            ?: throw IllegalStateException("Empty response")

        val data = methodResponse[1] as? JsonObject
        if ((methodResponse[0] as? JsonPrimitive)?.content == "error") {
            val errorType = (data?.get("type") as? JsonPrimitive)?.content
            throw JmapException("JMAP Error: $errorType")
        }
        return data ?: throw IllegalStateException("Invalid response format")
    }

    fun clearSession() {
        cached = null
    }

    private fun jmapSetException(operation: String, error: JmapSetError): Exception {
        val message = "$operation: ${error.type} - ${error.description}"
        return if (error.type == "rateLimit") {
            RateLimitedException(message)
        } else {
            JmapException(message)
        }
    }

    private companion object {
        /** Client-side id of the single record a create request carries. */
        const val CREATE_ID = "new1"
    }
}

open class JmapException(message: String) : Exception(message)


/** HTTP status -> domain failure; anything else (IOException, JMAP errors, ...) passes through. */
internal fun Throwable.asDomainFailure(): Throwable = when {
    this is HttpException && (code() == 401 || code() == 403) -> AuthRejectedException(this)
    this is HttpException && code() == 429 -> RateLimitedException(cause = this)
    this is HttpException && code() in 500..599 -> ServerErrorException(this)
    else -> this
}
