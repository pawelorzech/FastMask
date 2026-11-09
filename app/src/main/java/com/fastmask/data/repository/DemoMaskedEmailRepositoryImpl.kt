package com.fastmask.data.repository

import com.fastmask.data.demo.demoMasks
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

@Singleton
class DemoMaskedEmailRepositoryImpl @Inject constructor() : MaskedEmailRepository {

    private val state = MutableStateFlow(demoMasks(Instant.now()))

    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val changes: Flow<Unit> = _changes.asSharedFlow()

    /** Restores the seed masks, dated relative to [now] so they look as fresh as when demo mode was first entered. */
    fun reset(now: Instant = Instant.now()) {
        state.value = demoMasks(now)
    }

    override suspend fun getMaskedEmails(): Result<List<MaskedEmail>> {
        return Result.success(state.value)
    }

    override suspend fun getMaskedEmail(id: String): Result<MaskedEmail?> {
        return Result.success(state.value.find { it.id == id })
    }

    override suspend fun cachedMaskedEmails(): CachedMasks? = null

    override suspend fun createMaskedEmail(params: CreateMaskedEmailParams): Result<MaskedEmail> {
        val normalized = params.normalized().getOrElse { return Result.failure(it) }
        val newMask = buildNewMask(normalized)
        state.update { current -> current + newMask }
        _changes.tryEmit(Unit)
        return Result.success(newMask)
    }

    override suspend fun updateMaskedEmail(id: String, params: UpdateMaskedEmailParams): Result<Unit> {
        val normalized = params.normalized().getOrElse { return Result.failure(it) }
        return change(id) { mask ->
            mask.copy(
                state = normalized.state ?: mask.state,
                forDomain = normalized.forDomain ?: mask.forDomain,
                description = normalized.description ?: mask.description,
                url = if (normalized.clearsUrl) null else normalized.urlToSet ?: mask.url,
            )
        }
    }

    override suspend fun archiveMaskedEmail(id: String): Result<Unit> =
        change(id) { mask -> mask.copy(state = EmailState.DELETED) }

    override suspend fun destroyMaskedEmail(id: String): Result<Unit> = change(id) { null }

    private fun change(id: String, transform: (MaskedEmail) -> MaskedEmail?): Result<Unit> {
        var found = false
        state.update { current ->
            found = false
            current.mapNotNull { mask ->
                if (mask.id == id) {
                    found = true
                    transform(mask)
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
