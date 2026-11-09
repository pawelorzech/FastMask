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

    fun isRetryable(throwable: Throwable?): Boolean =
        of(throwable, fallback = R.string.error_generic).action == UiErrorAction.RETRY
}
