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
 */
object UiErrors {

    @StringRes
    fun messageRes(throwable: Throwable?, @StringRes fallback: Int): Int = when {
        throwable is IOException -> R.string.error_network
        throwable is HttpException && (throwable.code() == 401 || throwable.code() == 403) ->
            R.string.error_auth
        else -> fallback
    }
}
