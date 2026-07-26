package com.fastmask.data.repository

import com.fastmask.data.local.ProEntitlementStore
import com.fastmask.data.local.SettingsDataStore
import com.fastmask.domain.model.AppMode
import com.fastmask.domain.model.ProStatus
import com.fastmask.domain.repository.QuickMaskGuard
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuickMaskGuardImpl @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val proEntitlementStore: ProEntitlementStore,
) : QuickMaskGuard {

    override suspend fun appMode(): AppMode = settingsDataStore.appMode.first()

    override suspend fun appLockEnabled(): Boolean = settingsDataStore.appLockEnabled.first()

    override suspend fun isPro(): Boolean {
        // Match MainActivity's privacy gate: app lock must engage from the last
        // Play-VERIFIED cache, not after an async Play round-trip.
        return proEntitlementStore.read() == ProStatus.PRO
    }
}
