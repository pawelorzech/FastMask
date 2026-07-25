package com.fastmask.quickmask

import com.fastmask.R
import com.fastmask.domain.usecase.QuickMaskCreator
import com.fastmask.domain.usecase.QuickMaskResult
import com.fastmask.ui.common.copyToClipboard
import android.content.Context
import com.fastmask.ui.common.UiErrors
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Singleton
class QuickMaskRunner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val quickMaskCreator: QuickMaskCreator,
    private val notifier: QuickMaskNotifier,
) {

    // The shade can tear down TileService as soon as it collapses; this scope
    // belongs to the singleton runner so create/undo work survives that.
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun launchCreate(openApp: (() -> Unit)? = null) {
        scope.launch {
            val openAppAction = openApp ?: { context.startActivity(createAppLaunchIntent(context)) }
            val result = runCatching { quickMaskCreator.create() }
                .getOrElse { throwable ->
                    notifier.showFailure(
                        messageRes = UiErrors.messageRes(
                            throwable = throwable,
                            fallback = R.string.create_email_error_failed,
                        )
                    )
                    return@launch
                }

            when (result) {
                is QuickMaskResult.Created -> {
                    copyToClipboard(context, result.email)
                    notifier.showCreated(id = result.id, email = result.email)
                }
                QuickMaskResult.NotSignedIn -> {
                    withContext(Dispatchers.Main) { openAppAction() }
                }
                QuickMaskResult.DemoMode -> {
                    withContext(Dispatchers.Main) { openAppAction() }
                }
                QuickMaskResult.LockRequired -> {
                    // The tile lives above the lock screen; if app lock is armed,
                    // quick-create must hand off to the app's biometric gate.
                    withContext(Dispatchers.Main) { openAppAction() }
                }
                is QuickMaskResult.Failed -> {
                    notifier.showFailure(messageRes = result.messageRes)
                }
            }
        }
    }

    fun launchUndo(id: String, notificationId: Int) {
        scope.launch {
            val success = runCatching { quickMaskCreator.undo(id) }.getOrDefault(false)
            notifier.cancel(notificationId)
            notifier.showUndoResult(success = success)
        }
    }
}
