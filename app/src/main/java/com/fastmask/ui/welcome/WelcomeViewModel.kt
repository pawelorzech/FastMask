package com.fastmask.ui.welcome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastmask.data.local.SettingsDataStore
import com.fastmask.domain.model.AppMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WelcomeViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
) : ViewModel() {

    private val _events = MutableSharedFlow<WelcomeEvent>()
    val events: SharedFlow<WelcomeEvent> = _events.asSharedFlow()

    /**
     * Enter demo mode: flip the app mode flag and reset the tutorial flag so the
     * coach marks show the first time the user lands on the list. Order matters —
     * set [AppMode.DEMO] *before* clearing [SettingsDataStore.tutorialCompleted] so
     * downstream observers see the new mode the moment the tutorial flag changes.
     */
    fun enterDemoMode() {
        viewModelScope.launch {
            settingsDataStore.setAppMode(AppMode.DEMO)
            settingsDataStore.setTutorialCompleted(false)
            _events.emit(WelcomeEvent.EnterDemo)
        }
    }

    fun goToSignIn() {
        viewModelScope.launch {
            _events.emit(WelcomeEvent.GoToSignIn)
        }
    }
}

sealed class WelcomeEvent {
    data object EnterDemo : WelcomeEvent()
    data object GoToSignIn : WelcomeEvent()
}
