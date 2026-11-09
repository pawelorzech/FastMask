package com.fastmask.quickmask

import com.fastmask.domain.model.CreateMaskedEmailParams
import com.fastmask.domain.model.EmailState
import com.fastmask.domain.model.Session
import com.fastmask.domain.repository.AppLockSetting
import com.fastmask.domain.repository.MaskedEmailRepository
import com.fastmask.domain.repository.SessionRepository
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

    /**
     * Network/API failure. Carries the raw [cause]; turning it into a localized
     * message is the Android caller's job (`QuickMaskRunner` → `UiErrors`), so
     * the domain layer keeps no dependency on `R`/`ui`.
     */
    data class Failed(val cause: Throwable) : QuickMaskResult
}

/**
 * Quick-create orchestrator shared by the Quick Settings tile and the launcher
 * shortcut. Holds zero Android/TileService types so the whole decision table is
 * unit-testable.
 */
class QuickMaskCreator @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val appLockSetting: AppLockSetting,
    private val maskedEmailRepository: MaskedEmailRepository,
) {

    /** Runs the gates, then creates one ENABLED mask with no prefix. */
    suspend fun create(): QuickMaskResult {
        when (sessionRepository.current()) {
            Session.SIGNED_OUT -> return QuickMaskResult.NotSignedIn
            Session.DEMO -> return QuickMaskResult.DemoMode
            Session.ACCOUNT -> Unit
        }
        // The preference alone arms the lock, as in MainActivity's gate: a lapsed
        // Pro must not let the tile bypass a lock the owner switched on.
        if (appLockSetting.isAppLockEnabled()) {
            return QuickMaskResult.LockRequired
        }

        val params = CreateMaskedEmailParams(state = EmailState.ENABLED)
        return maskedEmailRepository.createMaskedEmail(params).fold(
            onSuccess = { mask ->
                QuickMaskResult.Created(id = mask.id, email = mask.email)
            },
            onFailure = { error -> QuickMaskResult.Failed(cause = error) }
        )
    }

    /**
     * The notification's "Undo" action: destroys the mask just created.
     *
     * Irreversible on purpose, and the one place in the app that is. The mask is
     * seconds old and was never given to anyone, so the user asking to take it
     * back means "remove it", not "archive it" — the opposite of what the
     * detail screen's Archive button promises.
     */
    suspend fun undo(id: String): Boolean = maskedEmailRepository.destroyMaskedEmail(id).isSuccess
}
