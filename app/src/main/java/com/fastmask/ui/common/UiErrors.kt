package com.fastmask.ui.common

import androidx.annotation.StringRes
import com.fastmask.R
import retrofit2.HttpException
import java.io.IOException

/**
 * Maps a repository/network [Throwable] to a localized, user-facing message
 * resource. Raw `Throwable.message` values ("Unable to resolve host…",
 * "HTTP 401") must never reach the UI — they are English-only and technical.
 *
 * Pure function → unit-testable without Android.
 *
 * Note: JMAP method-level errors (a `JmapException` from an HTTP-200 body,
 * e.g. `unknownAccountId`) intentionally fall through to the caller's
 * fallback — Fastmail rejects an invalid/revoked Bearer token at the
 * transport layer (HTTP 401), which IS mapped to [R.string.error_auth].
 */
object UiErrors {

    @StringRes
    fun messageRes(throwable: Throwable?, @StringRes fallback: Int): Int = when {
        throwable is IOException -> R.string.error_network
        throwable is HttpException && (throwable.code() == 401 || throwable.code() == 403) ->
            R.string.error_auth
        throwable is HttpException && throwable.code() == 429 -> R.string.error_rate_limit
        throwable is HttpException && throwable.code() in 500..599 -> R.string.error_server
        else -> fallback
    }

    /**
     * Whether repeating the identical request could plausibly succeed — i.e.
     * the failure is about the transport or the server, not about the input.
     *
     * Exactly the cases [messageRes] answers with a "try again" message.
     * Callers use it to decide whether to preserve user input for the retry
     * (see [com.fastmask.ui.auth.LoginViewModel]); an auth rejection (401/403)
     * is NOT retryable, so the credential is discarded there.
     */
    fun isRetryable(throwable: Throwable?): Boolean = when {
        throwable is IOException -> true
        throwable is HttpException && (throwable.code() == 401 || throwable.code() == 403) -> false
        throwable is HttpException && throwable.code() == 429 -> true
        throwable is HttpException && throwable.code() in 500..599 -> true
        else -> false
    }
}
