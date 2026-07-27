package com.fastmask.data.repository

import com.fastmask.data.local.SettingsDataStore
import com.fastmask.domain.model.AppMode
import com.fastmask.domain.repository.QuickMaskGuard
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuickMaskGuardImpl @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
) : QuickMaskGuard {

    override suspend fun appMode(): AppMode = settingsDataStore.appMode.first()

    override suspend fun appLockEnabled(): Boolean = settingsDataStore.appLockEnabled.first()
}
