package com.fastmask.data.repository

import com.fastmask.data.api.JmapApi
import com.fastmask.data.api.MaskedEmailDto
import com.fastmask.data.api.MaskedEmailUpdate
import com.fastmask.data.local.MaskedEmailCache
import com.fastmask.core.ApplicationScope
import com.fastmask.core.IoDispatcher
import com.fastmask.data.local.TokenStorage
import com.fastmask.domain.model.CachedMasks
import com.fastmask.domain.model.CreateMaskedEmailParams
import com.fastmask.domain.model.EmailState
import com.fastmask.domain.model.MaskedEmail
import com.fastmask.domain.model.MaskedEmailMetadata
import com.fastmask.domain.model.UpdateMaskedEmailParams
import com.fastmask.domain.repository.MaskedEmailRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Every method runs on the injected IO dispatcher.
 *
 * Retrofit resumes a suspend call on the caller's dispatcher, and every caller
 * here is a ViewModel on `Dispatchers.Main.immediate`. So although the HTTP
 * itself was off the main thread, everything around it was not: reading the
 * token initialises Tink and touches the KeyStore, and the write-through below
 * AES-encrypts the entire mask list and writes it to disk — on the UI thread,
 * once per refresh, growing with the size of the account.
 *
 * Mutations (create / update / archive / destroy) additionally run in the
 * application scope. A request that may already have reached Fastmail must not
 * be cancelled because the caller went away: backing out of the create screen
 * would leave a mask on the account that nobody ever sees, and cancelling an
 * archive or Undo leaves the UI and the account disagreeing. The caller may stop
 * waiting; the request still completes.
 */
@Singleton
class MaskedEmailRepositoryImpl @Inject constructor(
    private val jmapApi: JmapApi,
    private val tokenStorage: TokenStorage,
    private val cache: MaskedEmailCache,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @ApplicationScope private val appScope: CoroutineScope,
) : MaskedEmailRepository {

    /** Runs a mutation on [appScope]; cancelling the caller only abandons the wait. */
    private suspend fun <T> mutation(block: suspend () -> Result<T>): Result<T> =
        appScope.async(ioDispatcher) { block() }.await()

    override suspend fun getMaskedEmails(): Result<List<MaskedEmail>> = withContext(ioDispatcher) {
        val token = tokenStorage.getToken()
            ?: return@withContext Result.failure(IllegalStateException("Not authenticated"))

        // Captured BEFORE the network call: if a sign-out clears the cache while
        // this fetch is in flight, the write below is dropped instead of putting
        // the signed-out account's mask list back on disk. There is no
        // suspension point between the response and the write, so cancellation
        // cannot do this job.
        val generation = cache.currentGeneration()

        jmapApi.getMaskedEmails(token)
            .map { dtos -> dtos.map { it.toDomain() } }
            // Write through on every success, so the newest good answer is
            // always what an offline read gets. A cache write failure must not
            // fail the fetch — MaskedEmailCache swallows its own errors.
            .onSuccess { cache.write(it, owner = cacheOwner(token), generation = generation) }
    }

    override suspend fun getMaskedEmail(id: String): Result<MaskedEmail?> = withContext(ioDispatcher) {
        val token = tokenStorage.getToken()
            ?: return@withContext Result.failure(IllegalStateException("Not authenticated"))

        // Deliberately NO write-through: the disk cache holds whole-list
        // snapshots, and merging a single fresh mask into one would mean a
        // read-modify-write of the encrypted file on every detail open — the
        // exact cost this method exists to avoid. The snapshot catches up on
        // the next full list fetch.
        jmapApi.getMaskedEmail(token, id).map { it?.toDomain() }
    }

    override suspend fun cachedMaskedEmails(): CachedMasks? = withContext(ioDispatcher) {
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

    override suspend fun createMaskedEmail(params: CreateMaskedEmailParams): Result<MaskedEmail> = mutation {
        val token = tokenStorage.getToken()
            ?: return@mutation Result.failure(IllegalStateException("Not authenticated"))
        val forDomain = params.forDomain?.let { raw ->
            MaskedEmailMetadata.normalizeForDomain(raw)
                ?: return@mutation Result.failure(IllegalArgumentException("Invalid web origin"))
        }

        val create = CreateMaskedEmailParams(
            state = params.state,
            forDomain = forDomain?.takeIf { it.isNotBlank() },
            description = params.description?.takeIf { it.isNotBlank() },
            emailPrefix = params.emailPrefix?.takeIf { it.isNotBlank() },
            url = params.url?.takeIf { it.isNotBlank() }
        )

        jmapApi.createMaskedEmail(token, create).map { it.toDomain() }
    }

    override suspend fun updateMaskedEmail(id: String, params: UpdateMaskedEmailParams): Result<Unit> = mutation {
        val token = tokenStorage.getToken()
            ?: return@mutation Result.failure(IllegalStateException("Not authenticated"))
        val forDomain = params.forDomain?.let { raw ->
            MaskedEmailMetadata.normalizeForDomain(raw)
                ?: return@mutation Result.failure(IllegalArgumentException("Invalid web origin"))
        }

        val update = MaskedEmailUpdate(
            state = params.state,
            forDomain = forDomain,
            description = params.description,
            url = params.url?.takeIf { it.isNotEmpty() },
            clearUrl = params.url != null && params.url.isEmpty(),
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
    override suspend fun archiveMaskedEmail(id: String): Result<Unit> = mutation {
        val token = tokenStorage.getToken()
            ?: return@mutation Result.failure(IllegalStateException("Not authenticated"))

        jmapApi.updateMaskedEmail(
            token = token,
            id = id,
            update = MaskedEmailUpdate(state = EmailState.DELETED),
        )
    }

    override suspend fun destroyMaskedEmail(id: String): Result<Unit> = mutation {
        val token = tokenStorage.getToken()
            ?: return@mutation Result.failure(IllegalStateException("Not authenticated"))

        jmapApi.deleteMaskedEmail(token, id)
    }
}

private fun MaskedEmailDto.toDomain(): MaskedEmail {
    return MaskedEmail(
        id = id,
        email = email,
        state = state,
        forDomain = forDomain,
        description = description,
        createdBy = createdBy,
        url = url,
        emailPrefix = emailPrefix,
        createdAt = createdAt?.let { parseInstant(it) },
        lastMessageAt = lastMessageAt?.let { parseInstant(it) }
    )
}

/** Unparseable timestamps degrade to null rather than failing the whole list. */
private fun parseInstant(dateString: String): Instant? =
    runCatching { Instant.parse(dateString) }.getOrNull()
