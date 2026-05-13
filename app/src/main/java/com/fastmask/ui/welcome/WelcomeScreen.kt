package com.fastmask.ui.welcome

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fastmask.BuildConfig
import com.fastmask.R
import com.fastmask.ui.components.MonoEyebrow
import com.fastmask.ui.components.PillButton
import com.fastmask.ui.components.PillButtonVariant
import com.fastmask.ui.theme.FastMaskExtras
import com.fastmask.ui.theme.InstrumentSerif
import kotlinx.coroutines.flow.collectLatest

@Composable
fun WelcomeScreen(
    onSignIn: () -> Unit,
    onEnterDemo: () -> Unit,
    viewModel: WelcomeViewModel = hiltViewModel(),
) {
    val extras = FastMaskExtras.current

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is WelcomeEvent.EnterDemo -> onEnterDemo()
                is WelcomeEvent.GoToSignIn -> onSignIn()
            }
        }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 28.dp)
                .padding(top = 48.dp, bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Top,
        ) {
            MonoEyebrow(text = stringResource(R.string.login_eyebrow, BuildConfig.VERSION_NAME))
            Spacer(Modifier.height(36.dp))

            // App icon — uses launcher foreground (same hidden-eyes mark).
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(88.dp),
            )

            Spacer(Modifier.height(28.dp))

            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(12.dp))

            // Subtitle in serif with an accent word for character.
            val tagline = stringResource(R.string.welcome_tagline)
            val annotated = buildAnnotatedString {
                withStyle(
                    SpanStyle(
                        color = extras.inkSoft,
                        fontFamily = InstrumentSerif,
                        fontStyle = FontStyle.Italic,
                    ),
                ) { append(tagline) }
            }
            Text(
                text = annotated,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.fillMaxWidth(0.9f),
            )

            Spacer(Modifier.height(48.dp))

            // Primary CTA — sign in
            PillButton(
                text = stringResource(R.string.welcome_signin_cta),
                onClick = viewModel::goToSignIn,
                variant = PillButtonVariant.Primary,
                fullWidth = true,
                trailing = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = extras.onAccent,
                        modifier = Modifier.size(16.dp),
                    )
                },
            )

            Spacer(Modifier.height(12.dp))

            // Secondary CTA — try demo
            PillButton(
                text = stringResource(R.string.welcome_demo_cta),
                onClick = viewModel::enterDemoMode,
                variant = PillButtonVariant.Ghost,
                fullWidth = true,
            )

            Spacer(Modifier.height(36.dp))

            Text(
                text = stringResource(R.string.welcome_privacy_note),
                style = MaterialTheme.typography.bodySmall,
                color = extras.inkMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
