package com.fastmask.data.repository

import com.fastmask.data.api.JmapApi
import com.fastmask.data.api.MaskedEmailCreate
import com.fastmask.data.api.MaskedEmailDto
import com.fastmask.data.api.MaskedEmailState
import com.fastmask.data.api.MaskedEmailUpdate
import com.fastmask.data.local.MaskedEmailCache
import com.fastmask.data.local.TokenStorage
import com.fastmask.domain.model.CachedMasks
import com.fastmask.domain.model.CreateMaskedEmailParams
import com.fastmask.domain.model.EmailState
import com.fastmask.domain.model.MaskedEmail
import com.fastmask.domain.model.UpdateMaskedEmailParams
import com.fastmask.domain.repository.MaskedEmailRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Every method runs on [Dispatchers.IO].
 *
 * Retrofit resumes a suspend call on the caller's dispatcher, and every caller
 * here is a ViewModel on `Dispatchers.Main.immediate`. So although the HTTP
 * itself was off the main thread, everything around it was not: reading the
 * token initialises Tink and touches the KeyStore, and the write-through below
 * AES-encrypts the entire mask list and writes it to disk — on the UI thread,
 * once per refresh, growing with the size of the account.
 */
@Singleton
class MaskedEmailRepositoryImpl @Inject constructor(
    private val jmapApi: JmapApi,
    private val tokenStorage: TokenStorage,
    private val cache: MaskedEmailCache,
) : MaskedEmailRepository {

    override suspend fun getMaskedEmails(): Result<List<MaskedEmail>> = withContext(Dispatchers.IO) {
        val token = tokenStorage.getToken()
            ?: return@withContext Result.failure(IllegalStateException("Not authenticated"))

        jmapApi.getMaskedEmails(token)
            .map { dtos -> dtos.map { it.toDomain() } }
            // Write through on every success, so the newest good answer is
            // always what an offline read gets. A cache write failure must not
            // fail the fetch — MaskedEmailCache swallows its own errors.
            .onSuccess { cache.write(it, owner = cacheOwner(token)) }
    }

    override suspend fun cachedMaskedEmails(): CachedMasks? = withContext(Dispatchers.IO) {
        val token = tokenStorage.getToken() ?: return@withContext null
        cache.read(owner = cacheOwner(token))
    }

    /**
     * Opaque per-account marker for the offline snapshot: SHA-256 of the API
     * token, never the token itself.
     *
     * The JMAP account id would read better, but it is only known after a
     * successful session fetch — and the whole point of the snapshot is to be
     * readable with no network, where that fetch is exactly what failed. The
     * token is on disk either way and identifies the account just as well: a
     * different account means a different token, and a rotated token for the
     * same account means a mismatch, which costs one refetch.
     */
    private fun cacheOwner(token: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray())
            .joinToString("") { "%02x".format(it) }

    override suspend fun createMaskedEmail(params: CreateMaskedEmailParams): Result<MaskedEmail> = withContext(Dispatchers.IO) {
        val token = tokenStorage.getToken()
            ?: return@withContext Result.failure(IllegalStateException("Not authenticated"))

        val create = MaskedEmailCreate(
            state = params.state.toApi(),
            forDomain = params.forDomain?.takeIf { it.isNotBlank() },
            description = params.description?.takeIf { it.isNotBlank() },
            emailPrefix = params.emailPrefix?.takeIf { it.isNotBlank() },
            url = params.url?.takeIf { it.isNotBlank() }
        )

        jmapApi.createMaskedEmail(token, create).map { it.toDomain() }
    }

    override suspend fun updateMaskedEmail(id: String, params: UpdateMaskedEmailParams): Result<Unit> = withContext(Dispatchers.IO) {
        val token = tokenStorage.getToken()
            ?: return@withContext Result.failure(IllegalStateException("Not authenticated"))

        val update = MaskedEmailUpdate(
            state = params.state?.toApi(),
            forDomain = params.forDomain,
            description = params.description,
            url = params.url
        )

        jmapApi.updateMaskedEmail(token, id, update)
    }

    /**
     * A state flip, NOT `destroy`. A mask in state `deleted` stays on the
     * account — it appears under "Review deleted masked addresses" in Fastmail's
     * web UI and under this app's "Archived" filter — so the operation is
     * reversible, which is what the Archive dialog and the Undo snackbar
     * promise. `destroy` removes the record outright and leaves Undo updating
     * an id the server no longer knows.
     */
    override suspend fun archiveMaskedEmail(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        val token = tokenStorage.getToken()
            ?: return@withContext Result.failure(IllegalStateException("Not authenticated"))

        jmapApi.updateMaskedEmail(
            token = token,
            id = id,
            update = MaskedEmailUpdate(state = MaskedEmailState.DELETED),
        )
    }

    override suspend fun destroyMaskedEmail(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        val token = tokenStorage.getToken()
            ?: return@withContext Result.failure(IllegalStateException("Not authenticated"))

        jmapApi.deleteMaskedEmail(token, id)
    }
}

private fun MaskedEmailDto.toDomain(): MaskedEmail {
    return MaskedEmail(
        id = id,
        email = email,
        state = state.toDomain(),
        forDomain = forDomain,
        description = description,
        createdBy = createdBy,
        url = url,
        emailPrefix = emailPrefix,
        createdAt = createdAt?.let { parseInstant(it) },
        lastMessageAt = lastMessageAt?.let { parseInstant(it) }
    )
}

private fun MaskedEmailState.toDomain(): EmailState {
    return when (this) {
        MaskedEmailState.PENDING -> EmailState.PENDING
        MaskedEmailState.ENABLED -> EmailState.ENABLED
        MaskedEmailState.DISABLED -> EmailState.DISABLED
        MaskedEmailState.DELETED -> EmailState.DELETED
    }
}

private fun EmailState.toApi(): MaskedEmailState {
    return when (this) {
        EmailState.PENDING -> MaskedEmailState.PENDING
        EmailState.ENABLED -> MaskedEmailState.ENABLED
        EmailState.DISABLED -> MaskedEmailState.DISABLED
        EmailState.DELETED -> MaskedEmailState.DELETED
    }
}

private fun parseInstant(dateString: String): Instant? {
    return try {
        Instant.parse(dateString)
    } catch (e: Exception) {
        null
    }
}
