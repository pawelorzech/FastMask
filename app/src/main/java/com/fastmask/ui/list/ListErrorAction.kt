package com.fastmask.ui.list

import androidx.annotation.StringRes
import com.fastmask.R

/** The recovery action that matches a failed list request. */
internal enum class ListErrorAction {
    RETRY,
    REAUTHENTICATE,
}

internal fun listErrorActionFor(@StringRes errorRes: Int): ListErrorAction =
    if (errorRes == R.string.error_auth) {
        ListErrorAction.REAUTHENTICATE
    } else {
        ListErrorAction.RETRY
    }
