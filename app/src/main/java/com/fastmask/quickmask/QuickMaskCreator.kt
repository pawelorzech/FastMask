package com.fastmask.quickmask

import com.fastmask.domain.model.CreateMaskedEmailParams
import com.fastmask.domain.model.EmailState
import com.fastmask.domain.model.Session
import com.fastmask.domain.repository.AppLockSetting
import com.fastmask.domain.repository.MaskedEmailRepository
import com.fastmask.domain.repository.SessionRepository
import javax.inject.Inject

/** Every non-[Created] branch means NO mask was created. */
sealed interface QuickMaskResult {

    data class Created(val id: String, val email: String) : QuickMaskResult

    data object NotSignedIn : QuickMaskResult

    /** Demo mode — open the app; creating in-memory masks silently is a lie. */
    data object DemoMode : QuickMaskResult

    /** Biometric app lock is armed — the tile must not be a way around it. */
    data object LockRequired : QuickMaskResult

    data class Failed(val cause: Throwable) : QuickMaskResult
}

class QuickMaskCreator @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val appLockSetting: AppLockSetting,
    private val maskedEmailRepository: MaskedEmailRepository,
) {

    suspend fun create(): QuickMaskResult {
        when (sessionRepository.current()) {
            Session.SIGNED_OUT -> return QuickMaskResult.NotSignedIn
            Session.DEMO -> return QuickMaskResult.DemoMode
            Session.ACCOUNT -> Unit
        }
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
     * Irreversible on purpose, and the one place in the app that is. The mask is
     * seconds old and was never given to anyone, so the user asking to take it
     * back means "remove it", not "archive it" — the opposite of what the
     * detail screen's Archive button promises.
     */
    suspend fun undo(id: String): Boolean = maskedEmailRepository.destroyMaskedEmail(id).isSuccess
}
