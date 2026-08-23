package com.fastmask.domain.usecase

import com.fastmask.domain.model.MaskedEmail
import com.fastmask.domain.repository.MaskedEmailRepository
import javax.inject.Inject

/**
 * One mask by id. `null` (inside a success) means the server does not know
 * the mask; a failure means the fetch itself failed.
 */
class GetMaskedEmailUseCase @Inject constructor(
    private val repository: MaskedEmailRepository,
) {
    suspend operator fun invoke(id: String): Result<MaskedEmail?> =
        repository.getMaskedEmail(id)
}
