package com.fastmask.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastmask.R
import com.fastmask.data.local.SettingsDataStore
import com.fastmask.domain.model.AppMode
import com.fastmask.ui.theme.FastMaskTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Banner shown on screens that benefit from a hint that the user is in
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

    val haptic = LocalHapticFeedback.current
    DemoBannerContent(
        onSignInClick = {
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            viewModel.exitDemoMode {
                onSignInClick()
            }
        },
        modifier = modifier,
    )
}

/**
 * The stateless banner: a tonal `secondaryContainer` card with an icon, the
 * demo-mode notice and a "Sign in" text button (a stock M3 button, so it has
 * the 48dp target, the Button role and the press-morph for free).
 */
@Composable
fun DemoBannerContent(
    onSignInClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Science,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = stringResource(R.string.demo_banner_text),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onSignInClick) {
                Text(
                    text = stringResource(R.string.demo_banner_signin),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                )
            }
        }
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

    /**
     * Switch out of demo mode, reset the tutorial flag, then invoke [onDone]
     * (typically a navigation callback to the login screen). The callback is
     * fired *after* the state flip so that subsequent screens observe REAL mode.
     */
    fun exitDemoMode(onDone: () -> Unit) {
        // Same synchronous double-tap guard as every other mutating entry
        // point: the button stays enabled during the two DataStore writes, and
        // two taps in that window would each fire [onDone] — double navigation.
        if (exitInFlight) return
        exitInFlight = true
        // Swallow a DataStore write failure instead of crashing on the main
        // thread (viewModelScope rethrows uncaught exceptions); [onDone] only
        // runs after both writes succeed, so navigation never fires on a
        // half-applied state change. The guard releases on every terminal
        // outcome so a failed write can still be retried with the next tap.
        viewModelScope.launch(CoroutineExceptionHandler { _, _ ->
            exitInFlight = false
        }) {
            try {
                settingsDataStore.setAppMode(AppMode.REAL)
                settingsDataStore.setTutorialCompleted(false)
                onDone()
            } finally {
                exitInFlight = false
            }
        }
    }

    private var exitInFlight = false
}

@Preview(name = "Demo banner", showBackground = true)
@Composable
private fun DemoBannerPreview() {
    FastMaskTheme { DemoBannerContent(onSignInClick = {}) }
}

@Preview(name = "Demo banner dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DemoBannerDarkPreview() {
    FastMaskTheme { DemoBannerContent(onSignInClick = {}) }
}
