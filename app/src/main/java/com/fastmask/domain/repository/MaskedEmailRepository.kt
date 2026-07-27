package com.fastmask.domain.repository

import com.fastmask.domain.model.CreateMaskedEmailParams
import com.fastmask.domain.model.CachedMasks
import com.fastmask.domain.model.MaskedEmail
import com.fastmask.domain.model.UpdateMaskedEmailParams

interface MaskedEmailRepository {
    suspend fun getMaskedEmails(): Result<List<MaskedEmail>>

    /**
     * Last known good snapshot, for showing the list without a network.
     *
     * Deliberately NOT folded into [getMaskedEmails]: that call means "tell me
     * what the server says", and quietly answering it with old data would let
     * a caller present stale masks as current. Callers ask for this only after
     * a fetch failed, and must surface how old it is.
     *
     * @return null when nothing is cached (including demo mode, which is
     *   already in memory and never persisted).
     */
    suspend fun cachedMaskedEmails(): CachedMasks?

    suspend fun createMaskedEmail(params: CreateMaskedEmailParams): Result<MaskedEmail>
    suspend fun updateMaskedEmail(id: String, params: UpdateMaskedEmailParams): Result<Unit>
    suspend fun deleteMaskedEmail(id: String): Result<Unit>
}
