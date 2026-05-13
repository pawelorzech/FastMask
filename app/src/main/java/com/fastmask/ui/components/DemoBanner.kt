package com.fastmask.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastmask.R
import com.fastmask.data.local.SettingsDataStore
import com.fastmask.domain.model.AppMode
import com.fastmask.ui.theme.FastMaskExtras
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Slim banner shown on screens that benefit from a hint that the user is in
 * demo mode. The banner is self-contained: it observes [AppMode] internally,
 * hides itself when the app is in [AppMode.REAL], and renders a tap target
 * that switches the app back to real mode and emits a "go to sign-in" event
 * the caller can react to.
 *
 * Lives in [com.fastmask.ui.components] so all feature screens can drop it
 * in without owning demo-mode state themselves.
 */
@Composable
fun DemoBanner(
    onSignInClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DemoBannerViewModel = hiltViewModel(),
) {
    val appMode by viewModel.appMode.collectAsState()
    if (appMode != AppMode.DEMO) return

    val extras = FastMaskExtras.current
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.demo_banner_text),
            style = MaterialTheme.typography.labelLarge,
            color = extras.inkSoft,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = stringResource(R.string.demo_banner_signin),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = extras.accent,
            modifier = Modifier.clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.exitDemoMode {
                    onSignInClick()
                }
            },
        )
    }
}

@HiltViewModel
class DemoBannerViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
) : ViewModel() {

    val appMode: StateFlow<AppMode> = settingsDataStore.appMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = settingsDataStore.appModeBlocking(),
    )

    private val _events = MutableSharedFlow<DemoBannerEvent>()
    val events: SharedFlow<DemoBannerEvent> = _events.asSharedFlow()

    /**
     * Switch out of demo mode, reset the tutorial flag, then invoke [onDone]
     * (typically a navigation callback to the login screen). The callback is
     * fired *after* the state flip so that subsequent screens observe REAL mode.
     */
    fun exitDemoMode(onDone: () -> Unit) {
        viewModelScope.launch {
            settingsDataStore.setAppMode(AppMode.REAL)
            settingsDataStore.setTutorialCompleted(false)
            onDone()
        }
    }
}

sealed class DemoBannerEvent {
    data object GoToSignIn : DemoBannerEvent()
}
