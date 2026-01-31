package com.fastmask.domain.repository

import com.fastmask.domain.model.CreateMaskedEmailParams
import com.fastmask.domain.model.MaskedEmail
import com.fastmask.domain.model.UpdateMaskedEmailParams

interface MaskedEmailRepository {
    suspend fun getMaskedEmails(): Result<List<MaskedEmail>>
    suspend fun createMaskedEmail(params: CreateMaskedEmailParams): Result<MaskedEmail>
    suspend fun updateMaskedEmail(id: String, params: UpdateMaskedEmailParams): Result<Unit>
    suspend fun deleteMaskedEmail(id: String): Result<Unit>
}
