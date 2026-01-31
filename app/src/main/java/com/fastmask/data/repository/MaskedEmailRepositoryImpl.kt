package com.fastmask.data.repository

import com.fastmask.data.api.JmapApi
import com.fastmask.data.api.MaskedEmailCreate
import com.fastmask.data.api.MaskedEmailDto
import com.fastmask.data.api.MaskedEmailState
import com.fastmask.data.api.MaskedEmailUpdate
import com.fastmask.data.local.TokenStorage
import com.fastmask.domain.model.CreateMaskedEmailParams
import com.fastmask.domain.model.EmailState
import com.fastmask.domain.model.MaskedEmail
import com.fastmask.domain.model.UpdateMaskedEmailParams
import com.fastmask.domain.repository.MaskedEmailRepository
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MaskedEmailRepositoryImpl @Inject constructor(
    private val jmapApi: JmapApi,
    private val tokenStorage: TokenStorage
) : MaskedEmailRepository {

    override suspend fun getMaskedEmails(): Result<List<MaskedEmail>> {
        val token = tokenStorage.getToken()
            ?: return Result.failure(IllegalStateException("Not authenticated"))

        return jmapApi.getMaskedEmails(token).map { dtos ->
            dtos.map { it.toDomain() }
        }
    }

    override suspend fun createMaskedEmail(params: CreateMaskedEmailParams): Result<MaskedEmail> {
        val token = tokenStorage.getToken()
            ?: return Result.failure(IllegalStateException("Not authenticated"))

        val create = MaskedEmailCreate(
            state = params.state.toApi(),
            forDomain = params.forDomain?.takeIf { it.isNotBlank() },
            description = params.description?.takeIf { it.isNotBlank() },
            emailPrefix = params.emailPrefix?.takeIf { it.isNotBlank() },
            url = params.url?.takeIf { it.isNotBlank() }
        )

        return jmapApi.createMaskedEmail(token, create).map { it.toDomain() }
    }

    override suspend fun updateMaskedEmail(id: String, params: UpdateMaskedEmailParams): Result<Unit> {
        val token = tokenStorage.getToken()
            ?: return Result.failure(IllegalStateException("Not authenticated"))

        val update = MaskedEmailUpdate(
            state = params.state?.toApi(),
            forDomain = params.forDomain,
            description = params.description,
            url = params.url
        )

        return jmapApi.updateMaskedEmail(token, id, update)
    }

    override suspend fun deleteMaskedEmail(id: String): Result<Unit> {
        val token = tokenStorage.getToken()
            ?: return Result.failure(IllegalStateException("Not authenticated"))

        return jmapApi.deleteMaskedEmail(token, id)
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
