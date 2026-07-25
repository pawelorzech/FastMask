package com.fastmask.domain.repository

import com.fastmask.domain.model.AppMode

/**
 * The three preconditions the quick-create entry points (Quick Settings tile,
 * launcher shortcut) must consult before touching the Fastmail account.
 *
 * Deliberately NOT one folded `canCreateSilently()` boolean: each answer maps
 * to a different user-visible outcome (open the app on login, open the app in
 * demo, demand an unlock), and the app-lock rule is a conjunction the caller
 * has to be able to get wrong in a test.
 *
 * Implemented over `SettingsDataStore` + `ProEntitlementStore`; the interface
 * keeps the orchestrator free of Android types.
 */
interface QuickMaskGuard {

    /** Current runtime mode. [AppMode.DEMO] must never create a real mask. */
    suspend fun appMode(): AppMode

    /** The `app_lock_enabled` preference, regardless of entitlement. */
    suspend fun appLockEnabled(): Boolean

    /** Last VERIFIED entitlement (cache), matching MainActivity's P0 gate. */
    suspend fun isPro(): Boolean
}
