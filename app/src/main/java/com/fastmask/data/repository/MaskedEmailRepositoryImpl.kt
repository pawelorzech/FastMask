package com.fastmask.data.repository

import com.fastmask.data.api.JmapApi
import com.fastmask.data.api.MaskedEmailDto
import com.fastmask.data.local.MaskedEmailCache
import com.fastmask.core.ApplicationScope
import com.fastmask.core.IoDispatcher
import com.fastmask.core.sha256Hex
import com.fastmask.data.local.TokenStorage
import com.fastmask.domain.model.CachedMasks
import com.fastmask.domain.model.CreateMaskedEmailParams
import com.fastmask.domain.model.EmailState
import com.fastmask.domain.model.MaskedEmail
import com.fastmask.domain.model.UpdateMaskedEmailParams
import com.fastmask.domain.model.normalized
import com.fastmask.domain.repository.MaskedEmailRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Every method runs on the injected IO dispatcher: Retrofit resumes a suspend call
 * on the caller's dispatcher (`Dispatchers.Main.immediate` for ViewModels), and
 * reading the token (Tink, KeyStore) and the encrypted cache write-through are
 * disk work that grows with the size of the account.
 *
 * Mutations additionally run in the application scope. A request that may
 * already have reached Fastmail must not be cancelled because the caller went
 * away: that leaves a mask nobody sees, or a UI and account that disagree after
 * an archive or Undo.
 */
@Singleton
class MaskedEmailRepositoryImpl @Inject constructor(
    private val jmapApi: JmapApi,
    private val tokenStorage: TokenStorage,
    private val cache: MaskedEmailCache,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @ApplicationScope private val appScope: CoroutineScope,
) : MaskedEmailRepository {

    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val changes: Flow<Unit> = _changes.asSharedFlow()

    private suspend fun <T> mutation(block: suspend () -> Result<T>): Result<T> =
        appScope.async(ioDispatcher) {
            block().also { if (it.isSuccess) _changes.tryEmit(Unit) }
        }.await()

    private inline fun <T> withToken(block: (token: String) -> Result<T>): Result<T> {
        val token = tokenStorage.getToken()
            ?: return Result.failure(IllegalStateException("Not authenticated"))
        return block(token)
    }

    override suspend fun getMaskedEmails(): Result<List<MaskedEmail>> = withContext(ioDispatcher) {
        withToken { token ->
            cache.refresh(owner = cacheOwner(token)) {
                jmapApi.getMaskedEmails(token).map { dtos -> dtos.map { it.toDomain() } }
            }
        }
    }

    override suspend fun getMaskedEmail(id: String): Result<MaskedEmail?> = withContext(ioDispatcher) {
        withToken { token ->
            jmapApi.getMaskedEmail(token, id).map { it?.toDomain() }
        }
    }

    override suspend fun cachedMaskedEmails(): CachedMasks? = withContext(ioDispatcher) {
        val token = tokenStorage.getToken() ?: return@withContext null
        cache.read(owner = cacheOwner(token))
    }

    // The JMAP account id would read better, but it is only known after a
    // successful session fetch, which is what fails when offline.
    private fun cacheOwner(token: String): String = token.sha256Hex()

    override suspend fun createMaskedEmail(params: CreateMaskedEmailParams): Result<MaskedEmail> = mutation {
        withToken { token ->
            val create = params.normalized().getOrElse { return@withToken Result.failure(it) }
            jmapApi.createMaskedEmail(token, create).map { it.toDomain() }
        }
    }

    override suspend fun updateMaskedEmail(id: String, params: UpdateMaskedEmailParams): Result<Unit> = mutation {
        withToken { token ->
            val normalized = params.normalized().getOrElse { return@withToken Result.failure(it) }
            jmapApi.updateMaskedEmail(token, id, normalized)
        }
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
        withToken { token ->
            jmapApi.updateMaskedEmail(
                token = token,
                id = id,
                params = UpdateMaskedEmailParams(state = EmailState.DELETED),
            )
        }
    }

    override suspend fun destroyMaskedEmail(id: String): Result<Unit> = mutation {
        withToken { token -> jmapApi.deleteMaskedEmail(token, id) }
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

private fun parseInstant(dateString: String): Instant? =
    runCatching { Instant.parse(dateString) }.getOrNull()
