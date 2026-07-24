package com.fastmask.domain.usecase

import com.fastmask.domain.model.CachedMasks
import com.fastmask.domain.repository.MaskedEmailRepository
import javax.inject.Inject

/**
 * The last snapshot the app managed to fetch, for the offline case.
 *
 * Separate from [GetMaskedEmailsUseCase] on purpose: callers have to ask for
 * stale data explicitly, and what they get back carries its age so the UI can
 * say how old it is.
 */
class GetCachedMaskedEmailsUseCase @Inject constructor(
    private val repository: MaskedEmailRepository,
) {
    suspend operator fun invoke(): CachedMasks? = repository.cachedMaskedEmails()
}
