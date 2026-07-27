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

    /**
     * REVERSIBLE archive: moves the mask to [com.fastmask.domain.model.EmailState.DELETED].
     * Mail bounces, the address stays on the account, and restoring it is a
     * state flip back — which is exactly what the confirmation dialog ("Mail
     * sent here will bounce. You can restore it later.") and the list's Undo
     * snackbar promise, and what the "Archived" filter chip exists to show.
     *
     * Split from [destroyMaskedEmail] by audit 2026-07-27. Both used to be one
     * `deleteMaskedEmail`, sent as JMAP `destroy` against the real account
     * while the DEMO repository implemented it as a state flip. The only
     * automated test of "the highest-risk path in the app" ran in demo mode, so
     * it exercised the reversible semantics the UI promises and never touched
     * the irreversible ones the account actually received.
     */
    suspend fun archiveMaskedEmail(id: String): Result<Unit>

    /**
     * IRREVERSIBLE removal (JMAP `destroy`). The mask stops existing.
     *
     * Exactly one caller: the quick-create notification's "Undo", where the
     * user is disowning a mask created seconds ago by mistake. Never reachable
     * from the Archive button.
     */
    suspend fun destroyMaskedEmail(id: String): Result<Unit>
}
