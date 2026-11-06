package com.fastmask.data.api

import com.fastmask.core.runSuspendCatching
import com.fastmask.domain.auth.MaskedEmailScopeMissingException
import com.fastmask.domain.error.AuthRejectedException
import com.fastmask.domain.error.RateLimitedException
import com.fastmask.domain.error.ServerErrorException
import com.fastmask.domain.model.CreateMaskedEmailParams
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
    // @Volatile: the fast path in ensureSession() and the lock-free reads in
    // getAccountId()/getApiUrl() run on different Dispatchers.IO threads than
    // the writers. Without it there is no happens-before guarantee, so a reader
    // could see a stale null (redundant re-fetch) or a torn pair where
    // getApiUrl() still returns the default while getAccountId() is already set.
    @Volatile
    private var cachedSession: JmapSession? = null
    @Volatile
    private var cachedAccountId: String? = null
    @Volatile
    private var cachedToken: String? = null
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

    suspend fun getSession(token: String): Result<JmapSession> = remote {
        val session = jmapService.getSession(FastmailEndpoints.SESSION_URL, bearer(token))
        require(FastmailEndpoints.isTrustedHttpsUrl(session.apiUrl)) {
            "Untrusted apiUrl in session response"
        }
        val accountId = MaskedEmailScope.accountId(session)
            ?: throw MaskedEmailScopeMissingException() // Mail-only tokens used to borrow another account id and fail much later with an opaque masked-email error.
        cachedSession = session
        cachedAccountId = accountId
        cachedToken = token
        session
    }

    fun getAccountId(): String? = cachedAccountId

    fun getApiUrl(): String = cachedSession?.apiUrl ?: FastmailEndpoints.API_URL

    private suspend fun ensureSession(token: String): String {
        val accountId = cachedAccountId
        if (accountId != null && cachedToken == token) return accountId
        // Mutex so concurrent first calls fetch the session once instead of racing.
        sessionMutex.withLock {
            val lockedAccountId = cachedAccountId
            if (lockedAccountId != null && cachedToken == token) return lockedAccountId
            getSession(token).getOrThrow()
            return cachedAccountId
                ?: throw IllegalStateException("Failed to get account ID from session")
        }
    }

    suspend fun getMaskedEmails(token: String): Result<List<MaskedEmailDto>> = remote {
        fetchMaskedEmails(token, ids = null)
    }

    /**
     * One mask by id — the same JMAP `MaskedEmail/get` restricted with `ids`.
     *
     * The detail screen used to satisfy every open (and every post-edit
     * reload) with a full account fetch; on large accounts that turned "peek
     * at one address" into the most expensive read in the app. Returns null
     * when the server does not know the id (deleted elsewhere / bad deep link)
     * so callers can distinguish "absent" from "network failed".
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

        val created = response.created?.get(CREATE_ID)
            ?: throw IllegalStateException("Created email not found in response")
        MaskedEmailDto(
            id = created.id,
            email = created.email,
            state = created.state ?: create.state,
            forDomain = created.forDomain ?: create.forDomain,
            description = created.description ?: create.description.orEmpty(),
            createdBy = created.createdBy,
            url = created.url ?: create.url,
            createdAt = created.createdAt,
            lastMessageAt = created.lastMessageAt,
        )
    }

    suspend fun updateMaskedEmail(
        token: String,
        id: String,
        update: MaskedEmailUpdate
    ): Result<Unit> = remote {
        val updateObject = buildJsonObject {
            update.state?.let { put("state", JsonPrimitive(it.wire)) }
            update.forDomain?.let { put("forDomain", JsonPrimitive(it)) }
            update.description?.let { put("description", JsonPrimitive(it)) }
            when {
                update.clearUrl -> put("url", JsonNull)
                update.url != null -> put("url", JsonPrimitive(update.url))
            }
        }

        val response = call<MaskedEmailSetResponse>(token, "MaskedEmail/set") {
            put("update", buildJsonObject { put(id, updateObject) })
        }

        response.notUpdated?.get(id)?.let { error ->
            throw jmapSetException("Failed to update", error)
        }

        // Verify the update actually succeeded
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

        // Verify the destroy actually succeeded — mirrors the positive check in
        // updateMaskedEmail. Without it a silent server no-op would be
        // reported to the user as a successful archive.
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
        val accountId = ensureSession(token)

        val methodCall = buildJsonArray {
            add(JsonPrimitive(method))
            add(buildJsonObject {
                put("accountId", JsonPrimitive(accountId))
                args()
            })
            add(JsonPrimitive("0"))
        }

        val response = jmapService.executeMethod(
            url = getApiUrl(),
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
        cachedSession = null
        cachedAccountId = null
        cachedToken = null
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
