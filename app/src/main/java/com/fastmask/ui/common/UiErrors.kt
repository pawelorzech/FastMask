package com.fastmask.ui.common

import androidx.annotation.StringRes
import com.fastmask.R
import com.fastmask.domain.error.AuthRejectedException
import com.fastmask.domain.error.RateLimitedException
import com.fastmask.domain.error.ServerErrorException
import java.io.IOException

enum class UiErrorAction {
    RETRY,

    REAUTHENTICATE,

    NONE,
}

data class UiError(@StringRes val message: Int, val action: UiErrorAction)

/**
 * Raw `Throwable.message` values ("Unable to resolve host…", "HTTP 401") must never reach
 * the UI: they are English-only and technical.
 *
 * JMAP method-level errors (a `JmapException` from an HTTP-200 body) intentionally fall
 * through to the caller's fallback: Fastmail rejects an invalid/revoked Bearer token at
 * the transport layer (HTTP 401), reported as [AuthRejectedException].
 */
object UiErrors {

    /**
     * @param retryable whether the screen can repeat the failed operation. A refused
     *   token offers sign-in either way.
     */
    fun of(throwable: Throwable?, @StringRes fallback: Int, retryable: Boolean): UiError {
        val message = messageRes(throwable, fallback)
        val action = when {
            throwable is AuthRejectedException -> UiErrorAction.REAUTHENTICATE
            retryable -> UiErrorAction.RETRY
            else -> UiErrorAction.NONE
        }
        return UiError(message, action)
    }

    @StringRes
    fun messageRes(throwable: Throwable?, @StringRes fallback: Int): Int = when (throwable) {
        is IOException -> R.string.error_network
        is AuthRejectedException -> R.string.error_auth
        is RateLimitedException -> R.string.error_rate_limit
        is ServerErrorException -> R.string.error_server
        else -> fallback
    }

    /** Transient failures: trying the same thing again later can succeed. */
    fun isRetryable(throwable: Throwable?): Boolean =
        throwable is IOException || throwable is RateLimitedException || throwable is ServerErrorException
}
