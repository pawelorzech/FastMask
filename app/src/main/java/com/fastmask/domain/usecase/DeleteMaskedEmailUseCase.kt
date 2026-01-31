package com.fastmask.domain.usecase

import com.fastmask.domain.repository.MaskedEmailRepository
import javax.inject.Inject

class DeleteMaskedEmailUseCase @Inject constructor(
    private val repository: MaskedEmailRepository
) {
    suspend operator fun invoke(id: String): Result<Unit> {
        return repository.deleteMaskedEmail(id)
    }
}
