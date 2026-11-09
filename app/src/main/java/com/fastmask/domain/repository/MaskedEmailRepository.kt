package com.fastmask.domain.repository

import com.fastmask.domain.model.CreateMaskedEmailParams
import com.fastmask.domain.model.CachedMasks
import com.fastmask.domain.model.MaskedEmail
import com.fastmask.domain.model.UpdateMaskedEmailParams
import kotlinx.coroutines.flow.Flow

interface MaskedEmailRepository {
    /**
     * Emits once after every SUCCESSFUL mutation. Hot, no replay: a collector sees
     * only changes made while it is collecting, and bursts may be conflated into
     * one signal.
     */
    val changes: Flow<Unit>

    suspend fun getMaskedEmails(): Result<List<MaskedEmail>>

    /**
     * @return `Result.success(null)` when the server does not know the id —
     *   the mask was deleted elsewhere or the deep link is stale.
     *   `Result.failure` is a transport/server error; the caller may still
     *   have a cached copy worth showing (stale-while-revalidate).
     */
    suspend fun getMaskedEmail(id: String): Result<MaskedEmail?>

    /**
     * Deliberately NOT folded into [getMaskedEmails]: that call means "tell me
     * what the server says", and quietly answering it with old data would let
     * a caller present stale masks as current. Callers must surface how old it is.
     */
    suspend fun cachedMaskedEmails(): CachedMasks?

    suspend fun createMaskedEmail(params: CreateMaskedEmailParams): Result<MaskedEmail>
    suspend fun updateMaskedEmail(id: String, params: UpdateMaskedEmailParams): Result<Unit>

    /**
     * REVERSIBLE archive: moves the mask to [com.fastmask.domain.model.EmailState.DELETED].
     * Mail bounces, the address stays on the account, and restoring it is a
     * state flip back.
     */
    suspend fun archiveMaskedEmail(id: String): Result<Unit>

    /**
     * IRREVERSIBLE removal (JMAP `destroy`). The mask stops existing.
     */
    suspend fun destroyMaskedEmail(id: String): Result<Unit>
}
