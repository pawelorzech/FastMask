package com.fastmask.quickmask

import com.fastmask.core.runSuspendCatching
import com.fastmask.domain.repository.NotificationPromptSetting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The whole "ask for POST_NOTIFICATIONS once" rule in one place: load the
 * persisted flag, decide, mark the ask, persist it.
 *
 * [QuickMaskPolicy.shouldRequestNotificationPermission] is the pure predicate
 * ("does this snapshot warrant the prompt?"). It cannot answer "has this
 * process already asked?", because the persisted flag is written asynchronously
 * and a second snapshot can arrive before that write lands. MainActivity used
 * to evaluate the predicate exactly once, in `onCreate`, off the START
 * DESTINATION — so a user who launched signed out and signed in during the same
 * session (welcome → login → list) was never asked, and never saw the
 * quick-create confirmation that carries the only "Undo" action.
 *
 * So this is fed every session-state change and decides each time. It owes the
 * caller exactly one `true` per process, and none in later processes once the
 * ask has been persisted. The caller only launches the system request.
 *
 * @param scope where the flag is persisted; it only has to outlive the ask
 *   itself, so the Activity's lifecycle scope is enough.
 */
internal class NotificationPrompt(
    private val sdkInt: Int,
    private val setting: NotificationPromptSetting,
    private val scope: CoroutineScope,
) {

    // Until the persisted flag has been read, the safe answer is "already
    // asked": a startup race must never spend the one system prompt.
    @Volatile
    private var alreadyAsked = true

    /** Reads the persisted flag. Until this returns, [shouldAsk] says no. */
    suspend fun load() {
        alreadyAsked = setting.notificationPromptShown()
    }

    /**
     * @return true at most once, for the first snapshot that warrants the
     *   prompt. The ask is recorded before the dialog resolves: whatever the
     *   user answers, the app has now had its one ask, so the caller should
     *   launch the request right away.
     */
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

        // The in-memory latch is what stops a second snapshot; the write only
        // has to survive to the NEXT process.
        alreadyAsked = true
        scope.launch { runSuspendCatching { setting.setNotificationPromptShown(true) } }
        return true
    }
}
