package com.fastmask.domain.repository

/**
 * The persisted half of the "ask for notification permission once" rule: whether
 * the runtime prompt has already been shown on this install.
 */
interface NotificationPromptSetting {
    suspend fun notificationPromptShown(): Boolean

    suspend fun setNotificationPromptShown(shown: Boolean)
}
