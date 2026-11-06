package com.fastmask.data.repository

import com.fastmask.data.demo.INITIAL_DEMO_MASKS
import com.fastmask.domain.model.CachedMasks
import com.fastmask.domain.model.CreateMaskedEmailParams
import com.fastmask.domain.model.EmailState
import com.fastmask.domain.model.MaskedEmail
import com.fastmask.domain.model.UpdateMaskedEmailParams
import com.fastmask.domain.model.normalized
import com.fastmask.domain.repository.MaskedEmailRepository
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory implementation of [MaskedEmailRepository] used in demo mode.
 *
 * State lives only for the process; leaving demo mode and entering it again
 * always restarts from the same seed list.
 *
 * [archiveMaskedEmail] flips state to [EmailState.DELETED] (reversible, shown under
 * the "Archived" filter), while [destroyMaskedEmail] drops the entry (irreversible),
 * as the real repository does.
 */
@Singleton
class DemoMaskedEmailRepositoryImpl @Inject constructor() : MaskedEmailRepository {

    private val state = MutableStateFlow(INITIAL_DEMO_MASKS)

    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val changes: Flow<Unit> = _changes.asSharedFlow()

    /** Entering demo mode starts from the seed list again. */
    fun reset() {
        state.value = INITIAL_DEMO_MASKS
    }

    override suspend fun getMaskedEmails(): Result<List<MaskedEmail>> {
        return Result.success(state.value)
    }

    override suspend fun getMaskedEmail(id: String): Result<MaskedEmail?> {
        return Result.success(state.value.find { it.id == id })
    }

    /** Demo data already lives in memory and is never persisted. */
    override suspend fun cachedMaskedEmails(): CachedMasks? = null

    override suspend fun createMaskedEmail(params: CreateMaskedEmailParams): Result<MaskedEmail> {
        // The same parameter rules as the real repository, so demo mode shows
        // what an account would: a normalised origin, blanks as "not supplied",
        // and a refusal for an origin Fastmail would not take.
        val normalized = params.normalized().getOrElse { return Result.failure(it) }
        val newMask = buildNewMask(normalized)
        state.update { current -> current + newMask }
        _changes.tryEmit(Unit)
        return Result.success(newMask)
    }

    override suspend fun updateMaskedEmail(id: String, params: UpdateMaskedEmailParams): Result<Unit> {
        val normalized = params.normalized().getOrElse { return Result.failure(it) }
        var found = false
        state.update { current ->
            found = false
            current.map { mask ->
                if (mask.id == id) {
                    found = true
                    mask.copy(
                        state = normalized.state ?: mask.state,
                        forDomain = normalized.forDomain ?: mask.forDomain,
                        description = normalized.description ?: mask.description,
                        url = if (normalized.clearsUrl) null else normalized.urlToSet ?: mask.url,
                    )
                } else {
                    mask
                }
            }
        }
        return if (found) {
            _changes.tryEmit(Unit)
            Result.success(Unit)
        } else {
            Result.failure(NoSuchElementException("Demo mask not found: $id"))
        }
    }

    override suspend fun archiveMaskedEmail(id: String): Result<Unit> {
        var found = false
        state.update { current ->
            found = false
            current.map { mask ->
                if (mask.id == id) {
                    found = true
                    mask.copy(state = EmailState.DELETED)
                } else {
                    mask
                }
            }
        }
        return if (found) {
            _changes.tryEmit(Unit)
            Result.success(Unit)
        } else {
            Result.failure(NoSuchElementException("Demo mask not found: $id"))
        }
    }

    override suspend fun destroyMaskedEmail(id: String): Result<Unit> {
        var found = false
        state.update { current ->
            found = false
            current.filterNot { mask ->
                val matches = mask.id == id
                if (matches) found = true
                matches
            }
        }
        return if (found) {
            _changes.tryEmit(Unit)
            Result.success(Unit)
        } else {
            Result.failure(NoSuchElementException("Demo mask not found: $id"))
        }
    }

    /** [params] are already normalised: blank fields are null. */
    private fun buildNewMask(params: CreateMaskedEmailParams): MaskedEmail {
        val id = "demo-${UUID.randomUUID().toString().substring(0, 8)}"
        val prefix = params.emailPrefix ?: generatePrefix()
        val email = "$prefix@fastmask.com"
        val now = Instant.now()
        return MaskedEmail(
            id = id,
            email = email,
            state = params.state,
            forDomain = params.forDomain,
            description = params.description,
            createdBy = "demo",
            url = params.url,
            emailPrefix = prefix,
            createdAt = now,
            lastMessageAt = null
        )
    }

    private fun generatePrefix(): String {
        val word1 = WORDS_A.random()
        val word2 = WORDS_B.random()
        val digits = (100..999).random()
        return "$word1.$word2$digits"
    }

    companion object {
        private val WORDS_A = listOf(
            "quiet", "blue", "calm", "gentle", "warm", "bright", "clever",
            "swift", "mellow", "sharp", "bold", "kind", "wild", "soft"
        )
        private val WORDS_B = listOf(
            "harbor", "morning", "river", "bridge", "silk", "echo", "path",
            "cloud", "frost", "flame", "field", "stone", "valley", "sky"
        )
    }
}
