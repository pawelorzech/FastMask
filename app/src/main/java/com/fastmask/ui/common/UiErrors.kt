package com.fastmask.ui.common

import androidx.annotation.StringRes
import com.fastmask.R
import com.fastmask.domain.error.AuthRejectedException
import com.fastmask.domain.error.RateLimitedException
import com.fastmask.domain.error.ServerErrorException
import java.io.IOException

/** What the user can do about a failure. */
enum class UiErrorAction {
    /** Repeating the identical request could plausibly succeed. */
    RETRY,

    /** The credential was refused; only signing in again helps. */
    REAUTHENTICATE,

    /** Nothing sensible to offer (input or unknown problem). */
    NONE,
}

/** A failure as the UI shows it: a localized message and the matching recovery. */
data class UiError(@StringRes val message: Int, val action: UiErrorAction)

/**
 * Maps a repository/network [Throwable] to a [UiError]. Raw `Throwable.message`
 * values ("Unable to resolve host…", "HTTP 401") must never reach the UI: they
 * are English-only and technical. [of] is the single table; [messageRes] and
 * [isRetryable] read it.
 *
 * Note: JMAP method-level errors (a `JmapException` from an HTTP-200 body,
 * e.g. `unknownAccountId`) intentionally fall through to the caller's
 * fallback — Fastmail rejects an invalid/revoked Bearer token at the
 * transport layer (HTTP 401), which the data layer reports as
 * [AuthRejectedException] and which maps to [R.string.error_auth].
 */
object UiErrors {

    /**
     * @param fallback message for failures this table does not recognise.
     * @param unknownAction what to offer for those: a list load is always
     *   re-runnable (pass [UiErrorAction.RETRY]); a rejected form is not.
     */
    fun of(
        throwable: Throwable?,
        @StringRes fallback: Int,
        unknownAction: UiErrorAction = UiErrorAction.NONE,
    ): UiError = when (throwable) {
        is IOException -> UiError(R.string.error_network, UiErrorAction.RETRY)
        is AuthRejectedException -> UiError(R.string.error_auth, UiErrorAction.REAUTHENTICATE)
        is RateLimitedException -> UiError(R.string.error_rate_limit, UiErrorAction.RETRY)
        is ServerErrorException -> UiError(R.string.error_server, UiErrorAction.RETRY)
        else -> UiError(fallback, unknownAction)
    }

    @StringRes
    fun messageRes(throwable: Throwable?, @StringRes fallback: Int): Int =
        of(throwable, fallback).message

    /**
     * Whether repeating the identical request could plausibly succeed — i.e.
     * the failure is about the transport or the server, not about the input.
     * Callers use it to decide whether to preserve user input for the retry
     * (see [com.fastmask.ui.auth.LoginViewModel]); an auth rejection is NOT
     * retryable, so the credential is discarded there.
     */
    fun isRetryable(throwable: Throwable?): Boolean =
        of(throwable, fallback = R.string.error_generic).action == UiErrorAction.RETRY
}
