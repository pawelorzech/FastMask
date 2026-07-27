package com.fastmask.domain.usecase

import com.fastmask.domain.repository.MaskedEmailRepository
import javax.inject.Inject

/**
 * Reversible archive — the "Archive mask" button and the list's Undo snackbar.
 *
 * This file used to hold a single `DeleteMaskedEmailUseCase` serving two callers
 * with opposite intentions: this one, whose dialog promises "you can restore it
 * later", and quick-create Undo, which means "remove the thing I just made by
 * mistake". Both were sent as JMAP `destroy`, so the promise was the half that
 * broke. Two use cases, two verbs, no shared seam to confuse again.
 */
class ArchiveMaskedEmailUseCase @Inject constructor(
    private val repository: MaskedEmailRepository
) {
    suspend operator fun invoke(id: String): Result<Unit> {
        return repository.archiveMaskedEmail(id)
    }
}

/**
 * Irreversible removal — quick-create Undo only.
 *
 * The mask being destroyed here is seconds old and was never handed to anyone,
 * so leaving it archived would be exactly the clutter the user asked to be rid
 * of.
 */
class DestroyMaskedEmailUseCase @Inject constructor(
    private val repository: MaskedEmailRepository
) {
    suspend operator fun invoke(id: String): Result<Unit> {
        return repository.destroyMaskedEmail(id)
    }
}
