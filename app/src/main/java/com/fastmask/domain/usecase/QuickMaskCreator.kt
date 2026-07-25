package com.fastmask.domain.usecase

import androidx.annotation.StringRes
import com.fastmask.R
import com.fastmask.domain.model.AppMode
import com.fastmask.domain.model.CreateMaskedEmailParams
import com.fastmask.domain.model.EmailState
import com.fastmask.domain.repository.AuthRepository
import com.fastmask.domain.repository.QuickMaskGuard
import com.fastmask.ui.common.UiErrors
import javax.inject.Inject

/**
 * Outcome of a quick-create attempt. Every non-[Created] branch means NO mask
 * was created — the caller (tile / shortcut) reacts by opening the app or by
 * showing the reason, never by retrying silently.
 */
sealed interface QuickMaskResult {

    /** A real mask now exists on the account. [email] goes to the clipboard. */
    data class Created(val id: String, val email: String) : QuickMaskResult

    /** No Fastmail token — open the app on the welcome/login screen. */
    data object NotSignedIn : QuickMaskResult

    /** Demo mode — open the app; creating in-memory masks silently is a lie. */
    data object DemoMode : QuickMaskResult

    /** Biometric app lock is armed — the tile must not be a way around it. */
    data object LockRequired : QuickMaskResult

    /** Network/API failure, already mapped through `UiErrors` for the user. */
    data class Failed(@StringRes val messageRes: Int) : QuickMaskResult
}

/**
 * Quick-create orchestrator shared by the Quick Settings tile and the launcher
 * shortcut. Holds zero Android/TileService types so the whole decision table is
 * unit-testable.
 */
class QuickMaskCreator @Inject constructor(
    private val authRepository: AuthRepository,
    private val guard: QuickMaskGuard,
    private val createMaskedEmailUseCase: CreateMaskedEmailUseCase,
    private val deleteMaskedEmailUseCase: DeleteMaskedEmailUseCase,
) {

    /** Runs the gates, then creates one ENABLED mask with no prefix. */
    suspend fun create(): QuickMaskResult {
        if (!authRepository.isLoggedIn()) {
            return QuickMaskResult.NotSignedIn
        }
        if (guard.appMode() == AppMode.DEMO) {
            return QuickMaskResult.DemoMode
        }
        if (guard.appLockEnabled() && guard.isPro()) {
            return QuickMaskResult.LockRequired
        }

        val params = CreateMaskedEmailParams(
            state = EmailState.ENABLED,
            emailPrefix = null,
        )
        return createMaskedEmailUseCase(params).fold(
            onSuccess = { mask ->
                QuickMaskResult.Created(id = mask.id, email = mask.email)
            },
            onFailure = { error ->
                // Reuse the shared throwable->message mapping instead of forking it here.
                QuickMaskResult.Failed(
                    UiErrors.messageRes(error, R.string.create_email_error_failed)
                )
            }
        )
    }

    /** The notification's "Undo" action: deletes the mask just created. */
    suspend fun undo(id: String): Boolean = deleteMaskedEmailUseCase(id).isSuccess
}
