package com.fastmask.domain.usecase

import com.fastmask.domain.model.CreateMaskedEmailParams
import com.fastmask.domain.model.MaskedEmail
import com.fastmask.domain.repository.MaskedEmailRepository
import javax.inject.Inject

class CreateMaskedEmailUseCase @Inject constructor(
    private val repository: MaskedEmailRepository
) {
    suspend operator fun invoke(params: CreateMaskedEmailParams): Result<MaskedEmail> {
        if (params.emailPrefix != null) {
            val prefix = params.emailPrefix
            if (prefix.length > 64) {
                return Result.failure(IllegalArgumentException("Email prefix must be 64 characters or less"))
            }
            if (!prefix.matches(Regex("^[a-z0-9_]*$"))) {
                return Result.failure(IllegalArgumentException("Email prefix can only contain lowercase letters, numbers, and underscores"))
            }
        }
        return repository.createMaskedEmail(params)
    }
}
