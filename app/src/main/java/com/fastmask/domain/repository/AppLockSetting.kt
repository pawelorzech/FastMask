package com.fastmask.domain.repository

/**
 * The `app_lock_enabled` preference. On its own it arms the lock: Pro gates only
 * turning it on, so a lapsed purchase never disarms a lock the user chose.
 */
interface AppLockSetting {
    suspend fun isAppLockEnabled(): Boolean
}
