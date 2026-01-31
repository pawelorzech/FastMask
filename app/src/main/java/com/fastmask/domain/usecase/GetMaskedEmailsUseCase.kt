package com.fastmask.domain.usecase

import com.fastmask.domain.model.MaskedEmail
import com.fastmask.domain.repository.MaskedEmailRepository
import javax.inject.Inject

class GetMaskedEmailsUseCase @Inject constructor(
    private val repository: MaskedEmailRepository
) {
    suspend operator fun invoke(): Result<List<MaskedEmail>> {
        return repository.getMaskedEmails()
    }
}
