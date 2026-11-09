package com.fastmask.domain.repository

interface NotificationPromptSetting {
    suspend fun notificationPromptShown(): Boolean

    suspend fun setNotificationPromptShown(shown: Boolean)
}
