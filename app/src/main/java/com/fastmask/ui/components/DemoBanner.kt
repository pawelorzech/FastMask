package com.fastmask.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
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
import com.fastmask.domain.repository.SessionRepository
import com.fastmask.domain.model.Session
import com.fastmask.ui.theme.FastMaskTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Banner shown on screens that benefit from a hint that the user is in
 * demo mode. It hides itself outside the demo, and its action ends the demo
 * before handing control to [onSignInClick].
 */
@Composable
fun DemoBanner(
    onSignInClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DemoBannerViewModel = hiltViewModel(),
) {
    val session by viewModel.session.collectAsState()
    if (!session.isDemo) return

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
    // At large font scales the side-by-side layout leaves the text a sliver of
    // width (icon + button take ~40%), so it wraps to four lines and the banner eats
    // a third of the list. Stack it instead: text on the full width, the action as
    // an icon-plus-label button underneath. Nothing is truncated.
    val stacked = LocalDensity.current.fontScale >= STACKED_FONT_SCALE
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = if (stacked) 4.dp else 8.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        if (stacked) {
            Column(
                modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
            ) {
                Text(
                    text = stringResource(R.string.demo_banner_text),
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(
                    onClick = onSignInClick,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Login,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.demo_banner_signin),
                        style = MaterialTheme.typography.labelLargeEmphasized,
                    )
                }
            }
            return@Surface
        }
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

/** From this font scale up the banner stacks its text above the action. */
private const val STACKED_FONT_SCALE = 1.5f

@HiltViewModel
class DemoBannerViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    val session: StateFlow<Session> = sessionRepository.session

    /** Ends the demo, then calls [onDone], so the next screen already sees the signed-out session. */
    fun exitDemoMode(onDone: () -> Unit) {
        // Same synchronous double-tap guard as every other mutating entry
        // point: the button stays enabled during the sign-out, and
        // two taps in that window would each fire [onDone] — double navigation.
        if (exitInFlight) return
        exitInFlight = true
        // Swallow a DataStore write failure instead of crashing on the main
        // thread (viewModelScope rethrows uncaught exceptions); [onDone] only
        // runs after sign-out succeeds, so navigation never fires on a
        // half-applied state change. The guard releases on every terminal
        // outcome so a failed write can still be retried with the next tap.
        viewModelScope.launch(CoroutineExceptionHandler { _, _ ->
            exitInFlight = false
        }) {
            try {
                sessionRepository.signOut()
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
