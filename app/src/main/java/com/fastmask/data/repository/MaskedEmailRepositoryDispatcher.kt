package com.fastmask.data.repository

import com.fastmask.domain.model.CachedMasks
import com.fastmask.domain.model.CreateMaskedEmailParams
import com.fastmask.domain.model.MaskedEmail
import com.fastmask.domain.model.UpdateMaskedEmailParams
import com.fastmask.domain.repository.MaskedEmailRepository
import com.fastmask.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.merge
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MaskedEmailRepositoryDispatcher @Inject constructor(
    private val realRepo: MaskedEmailRepositoryImpl,
    private val demoRepo: DemoMaskedEmailRepositoryImpl,
    private val sessionRepository: SessionRepository,
) : MaskedEmailRepository {

    override val changes: Flow<Unit> = merge(realRepo.changes, demoRepo.changes)

    private suspend fun current(): MaskedEmailRepository =
        if (sessionRepository.current().isDemo) demoRepo else realRepo

    override suspend fun getMaskedEmails(): Result<List<MaskedEmail>> =
        current().getMaskedEmails()

    override suspend fun getMaskedEmail(id: String): Result<MaskedEmail?> =
        current().getMaskedEmail(id)

    override suspend fun cachedMaskedEmails(): CachedMasks? =
        current().cachedMaskedEmails()

    override suspend fun createMaskedEmail(params: CreateMaskedEmailParams): Result<MaskedEmail> =
        current().createMaskedEmail(params)

    override suspend fun updateMaskedEmail(id: String, params: UpdateMaskedEmailParams): Result<Unit> =
        current().updateMaskedEmail(id, params)

    override suspend fun archiveMaskedEmail(id: String): Result<Unit> =
        current().archiveMaskedEmail(id)

    override suspend fun destroyMaskedEmail(id: String): Result<Unit> =
        current().destroyMaskedEmail(id)
}
