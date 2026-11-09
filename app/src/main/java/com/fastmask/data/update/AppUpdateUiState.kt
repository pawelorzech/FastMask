package com.fastmask.data.update

sealed interface AppUpdateUiState {
    data object Idle : AppUpdateUiState

    data object ReadyToInstall : AppUpdateUiState
}
