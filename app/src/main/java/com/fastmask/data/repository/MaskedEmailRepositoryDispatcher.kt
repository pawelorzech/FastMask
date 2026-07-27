package com.fastmask.data.repository

import com.fastmask.data.local.SettingsDataStore
import com.fastmask.domain.model.AppMode
import com.fastmask.domain.model.CachedMasks
import com.fastmask.domain.model.CreateMaskedEmailParams
import com.fastmask.domain.model.MaskedEmail
import com.fastmask.domain.model.UpdateMaskedEmailParams
import com.fastmask.domain.repository.MaskedEmailRepository
import kotlinx.coroutines.flow.first
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
 * The mode read is a suspending [SettingsDataStore.appMode] collection, not the blocking
 * variant it used to be: `appModeBlocking` wraps `runBlocking`, and every caller here is a
 * ViewModel on the main dispatcher, so each CRUD call parked the UI thread on a DataStore
 * read. The flow's first emission comes from the same in-memory cache after the first hit.
 */
@Singleton
class MaskedEmailRepositoryDispatcher @Inject constructor(
    @Named("real") private val realRepo: MaskedEmailRepository,
    @Named("demo") private val demoRepo: MaskedEmailRepository,
    private val settingsDataStore: SettingsDataStore
) : MaskedEmailRepository {

    private suspend fun current(): MaskedEmailRepository =
        if (settingsDataStore.appMode.first() == AppMode.DEMO) demoRepo else realRepo

    override suspend fun getMaskedEmails(): Result<List<MaskedEmail>> =
        current().getMaskedEmails()

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
