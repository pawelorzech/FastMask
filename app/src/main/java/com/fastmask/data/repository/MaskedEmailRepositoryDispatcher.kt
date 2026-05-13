package com.fastmask.data.repository

import com.fastmask.data.local.SettingsDataStore
import com.fastmask.domain.model.AppMode
import com.fastmask.domain.model.CreateMaskedEmailParams
import com.fastmask.domain.model.MaskedEmail
import com.fastmask.domain.model.UpdateMaskedEmailParams
import com.fastmask.domain.repository.MaskedEmailRepository
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Runtime router for [MaskedEmailRepository].
 *
 * Reads the current [AppMode] from [SettingsDataStore] on every call and delegates to
 * either the real (JMAP-backed) or demo (in-memory) implementation. This is wired as
 * the default Hilt binding for [MaskedEmailRepository] so ViewModels keep injecting the
 * interface without changes.
 *
 * The cost of [SettingsDataStore.appModeBlocking] per call is acceptable because DataStore
 * caches reads in memory after the first hit and these calls are not on hot UI paths
 * (only on explicit CRUD invocations from ViewModels).
 */
@Singleton
class MaskedEmailRepositoryDispatcher @Inject constructor(
    @Named("real") private val realRepo: MaskedEmailRepository,
    @Named("demo") private val demoRepo: MaskedEmailRepository,
    private val settingsDataStore: SettingsDataStore
) : MaskedEmailRepository {

    private fun current(): MaskedEmailRepository =
        if (settingsDataStore.appModeBlocking() == AppMode.DEMO) demoRepo else realRepo

    override suspend fun getMaskedEmails(): Result<List<MaskedEmail>> =
        current().getMaskedEmails()

    override suspend fun createMaskedEmail(params: CreateMaskedEmailParams): Result<MaskedEmail> =
        current().createMaskedEmail(params)

    override suspend fun updateMaskedEmail(id: String, params: UpdateMaskedEmailParams): Result<Unit> =
        current().updateMaskedEmail(id, params)

    override suspend fun deleteMaskedEmail(id: String): Result<Unit> =
        current().deleteMaskedEmail(id)
}
