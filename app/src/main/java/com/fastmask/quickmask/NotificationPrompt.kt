package com.fastmask.quickmask

import com.fastmask.core.runSuspendCatching
import com.fastmask.domain.repository.NotificationPromptSetting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class NotificationPrompt(
    private val sdkInt: Int,
    private val setting: NotificationPromptSetting,
    private val scope: CoroutineScope,
) {

    // Until the persisted flag has been read, the safe answer is "already
    // asked": a startup race must never spend the one system prompt.
    @Volatile
    private var alreadyAsked = true

    suspend fun load() {
        alreadyAsked = setting.notificationPromptShown()
    }

    fun shouldAsk(
        permissionGranted: Boolean,
        signedIn: Boolean,
        locked: Boolean,
        demoMode: Boolean,
    ): Boolean {
        val warranted = QuickMaskPolicy.shouldRequestNotificationPermission(
            sdkInt = sdkInt,
            permissionGranted = permissionGranted,
            alreadyAsked = alreadyAsked,
            signedIn = signedIn,
            locked = locked,
            demoMode = demoMode,
        )
        if (!warranted) return false

        alreadyAsked = true
        scope.launch { runSuspendCatching { setting.setNotificationPromptShown(true) } }
        return true
    }
}
