package com.fastmask.data.repository

import com.fastmask.data.demo.INITIAL_DEMO_MASKS
import com.fastmask.domain.model.CreateMaskedEmailParams
import com.fastmask.domain.model.EmailState
import com.fastmask.domain.model.MaskedEmail
import com.fastmask.domain.model.UpdateMaskedEmailParams
import com.fastmask.domain.repository.MaskedEmailRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory implementation of [MaskedEmailRepository] used in demo mode.
 *
 * Backed by a [MutableStateFlow] of [MaskedEmail]. CRUD operations mutate this list and
 * return a snapshot. No persistence — state survives only for the lifetime of the
 * application process, which is intentional: leaving demo mode (logout / "Sign in")
 * should always come back to the same pristine seed list.
 *
 * `deleteMaskedEmail` archives by flipping state to [EmailState.DELETED] rather than
 * removing the entry, mirroring how Fastmail's JMAP API behaves and so the "Archived"
 * filter chip in the UI has content to show.
 */
@Singleton
class DemoMaskedEmailRepositoryImpl @Inject constructor() : MaskedEmailRepository {

    private val state = MutableStateFlow(INITIAL_DEMO_MASKS)

    override suspend fun getMaskedEmails(): Result<List<MaskedEmail>> {
        return Result.success(state.value)
    }

    override suspend fun createMaskedEmail(params: CreateMaskedEmailParams): Result<MaskedEmail> {
        val newMask = buildNewMask(params)
        state.update { current -> current + newMask }
        return Result.success(newMask)
    }

    override suspend fun updateMaskedEmail(id: String, params: UpdateMaskedEmailParams): Result<Unit> {
        var found = false
        state.update { current ->
            current.map { mask ->
                if (mask.id == id) {
                    found = true
                    mask.copy(
                        state = params.state ?: mask.state,
                        forDomain = params.forDomain ?: mask.forDomain,
                        description = params.description ?: mask.description,
                        url = params.url ?: mask.url
                    )
                } else {
                    mask
                }
            }
        }
        return if (found) {
            Result.success(Unit)
        } else {
            Result.failure(NoSuchElementException("Demo mask not found: $id"))
        }
    }

    override suspend fun deleteMaskedEmail(id: String): Result<Unit> {
        var found = false
        state.update { current ->
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
            Result.success(Unit)
        } else {
            Result.failure(NoSuchElementException("Demo mask not found: $id"))
        }
    }

    private fun buildNewMask(params: CreateMaskedEmailParams): MaskedEmail {
        val id = "demo-${UUID.randomUUID().toString().substring(0, 8)}"
        val prefix = params.emailPrefix?.takeIf { it.isNotBlank() }
            ?: generatePrefix()
        val email = "$prefix@fastmask.com"
        val now = Instant.now()
        return MaskedEmail(
            id = id,
            email = email,
            state = params.state,
            forDomain = params.forDomain?.takeIf { it.isNotBlank() },
            description = params.description?.takeIf { it.isNotBlank() },
            createdBy = "demo",
            url = params.url?.takeIf { it.isNotBlank() },
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
