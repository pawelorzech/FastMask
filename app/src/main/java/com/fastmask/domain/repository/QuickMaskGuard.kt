package com.fastmask.domain.repository

import com.fastmask.domain.model.AppMode

/**
 * The two preconditions the quick-create entry points (Quick Settings tile,
 * launcher shortcut) must consult before touching the Fastmail account.
 *
 * Deliberately NOT one folded `canCreateSilently()` boolean: each answer maps
 * to a different user-visible outcome (open the app on login, open the app in
 * demo, demand an unlock).
 *
 * There used to be a third — `isPro()` — because the lock was gated on the
 * entitlement as well as the preference. It is gone on purpose: an armed lock
 * is armed whoever paid for it, and consulting the entitlement here let the
 * tile create a mask past a lock the user had switched on. Pro now gates only
 * the act of ENABLING the lock, in `SettingsViewModel.onAppLockToggled`.
 *
 * Implemented over `SettingsDataStore`; the interface keeps the orchestrator
 * free of Android types.
 */
interface QuickMaskGuard {

    /** Current runtime mode. [AppMode.DEMO] must never create a real mask. */
    suspend fun appMode(): AppMode

    /** The `app_lock_enabled` preference — on its own, this arms the lock. */
    suspend fun appLockEnabled(): Boolean
}
