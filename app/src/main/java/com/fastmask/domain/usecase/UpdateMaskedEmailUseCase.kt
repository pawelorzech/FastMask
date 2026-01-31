package com.fastmask.domain.usecase

import com.fastmask.domain.model.UpdateMaskedEmailParams
import com.fastmask.domain.repository.MaskedEmailRepository
import javax.inject.Inject

class UpdateMaskedEmailUseCase @Inject constructor(
    private val repository: MaskedEmailRepository
) {
    suspend operator fun invoke(id: String, params: UpdateMaskedEmailParams): Result<Unit> {
        return repository.updateMaskedEmail(id, params)
    }
}
