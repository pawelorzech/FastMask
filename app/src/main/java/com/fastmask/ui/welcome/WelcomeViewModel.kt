package com.fastmask.ui.welcome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastmask.data.local.SettingsDataStore
import com.fastmask.domain.model.AppMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WelcomeViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
) : ViewModel() {

    // Channel-backed one-time events: buffered delivery survives windows with
    // no active collector (e.g. mid-rotation) and each event is handled once.
    private val _events = Channel<WelcomeEvent>(Channel.BUFFERED)
    val events: Flow<WelcomeEvent> = _events.receiveAsFlow()

    // DataStore writes can throw (disk full, corrupted file) and viewModelScope
    // rethrows uncaught exceptions on the main thread — an unguarded write here
    // crashes the app on the very first tap of "Try demo". Matches the handler
    // already present in SettingsViewModel / MaskedEmailListViewModel. Aborting
    // before _events.send() is deliberate: the user stays on Welcome rather
    // than navigating into a demo whose mode flag was never persisted.
    private val writeErrorHandler = CoroutineExceptionHandler { _, _ -> }

    /**
     * Enter demo mode: flip the app mode flag and reset the tutorial flag so the
     * coach marks show the first time the user lands on the list. Order matters —
     * set [AppMode.DEMO] *before* clearing [SettingsDataStore.tutorialCompleted] so
     * downstream observers see the new mode the moment the tutorial flag changes.
     */
    fun enterDemoMode() {
        viewModelScope.launch(writeErrorHandler) {
            settingsDataStore.setAppMode(AppMode.DEMO)
            settingsDataStore.setTutorialCompleted(false)
            _events.send(WelcomeEvent.EnterDemo)
        }
    }

    fun goToSignIn() {
        viewModelScope.launch {
            _events.send(WelcomeEvent.GoToSignIn)
        }
    }
}

sealed class WelcomeEvent {
    data object EnterDemo : WelcomeEvent()
    data object GoToSignIn : WelcomeEvent()
}
