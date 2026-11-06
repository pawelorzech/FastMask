package com.fastmask.ui.update

/** What the UI is allowed to know. Free of Play types on purpose — see `AppUpdateController`. */
sealed interface AppUpdateUiState {
    data object Idle : AppUpdateUiState

    /** Bytes are on the device. The only thing left is the user agreeing to restart. */
    data object ReadyToInstall : AppUpdateUiState
}
