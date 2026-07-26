package com.fastmask.ui.auth

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fastmask.BuildConfig
import com.fastmask.R
import com.fastmask.ui.common.FastmailLinks
import com.fastmask.ui.common.openExternalIntent
import com.fastmask.ui.components.DesignCard
import com.fastmask.ui.components.DesignInput
import com.fastmask.ui.components.MonoEyebrow
import com.fastmask.ui.components.MonoLabel
import com.fastmask.ui.components.PillButton
import com.fastmask.ui.components.PillButtonVariant
import com.fastmask.ui.theme.FastMaskExtras
import com.fastmask.ui.theme.InstrumentSerif

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onEnterDemo: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var showToken by remember { mutableStateOf(false) }
    var settingsOpenFailed by remember { mutableStateOf(false) }
    val extras = FastMaskExtras.current
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is LoginEvent.LoginSuccess -> onLoginSuccess()
                is LoginEvent.EnterDemo -> onEnterDemo()
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
            verticalArrangement = Arrangement.Center,
        ) {
            // Eyebrow
            MonoEyebrow(text = stringResource(R.string.login_eyebrow, BuildConfig.VERSION_NAME))
            Spacer(Modifier.height(14.dp))

            // Hero — A quiet place for [accent]masked mail[/accent].
            val heroPrefix = stringResource(R.string.login_hero_prefix)
            val heroAccent = stringResource(R.string.login_hero_accent)
            val heroSuffix = stringResource(R.string.login_hero_suffix)
            val annotated = buildAnnotatedString {
                withStyle(
                    SpanStyle(
                        color = MaterialTheme.colorScheme.onBackground,
                        fontFamily = InstrumentSerif,
                    ),
                ) { append(heroPrefix) }
                append(" ")
                withStyle(
                    SpanStyle(
                        color = extras.accent,
                        fontStyle = FontStyle.Italic,
                        fontFamily = InstrumentSerif,
                    ),
                ) { append(heroAccent) }
                withStyle(
                    SpanStyle(
                        color = MaterialTheme.colorScheme.onBackground,
                        fontFamily = InstrumentSerif,
                    ),
                ) { append(heroSuffix) }
            }
            Text(
                text = annotated,
                style = MaterialTheme.typography.displayLarge,
            )
            Spacer(Modifier.height(18.dp))

            Text(
                text = stringResource(R.string.login_intro),
                color = extras.inkSoft,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth(0.85f),
            )

            Spacer(Modifier.height(36.dp))

            // Token input. Paste and show/hide both live in the field's
            // trailing slot rather than beside the field: DesignInput is a
            // Column whose height grows when the error hint appears, so a
            // sibling button would jump down at exactly the moment the user
            // reaches for Paste to correct the token.
            DesignInput(
                value = uiState.token,
                onValueChange = viewModel::onTokenChange,
                label = stringResource(R.string.login_api_token_label),
                placeholder = stringResource(R.string.login_api_token_placeholder),
                mono = true,
                isError = uiState.errorRes != null,
                enabled = !uiState.isLoading,
                visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { viewModel.login() }),
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Reads the clipboard ONLY from this tap. Never on
                        // screen entry or resume — Android 12+ shows a system
                        // toast for every clipboard read, and a privacy-first
                        // app must not trigger one the user did not ask for.
                        TokenFieldIconButton(
                            icon = Icons.Filled.ContentPaste,
                            contentDescription = stringResource(R.string.login_paste_button),
                            enabled = !uiState.isLoading,
                            onClick = {
                                val clipboardText = clipboardManager.getText()?.text
                                if (!clipboardText.isNullOrEmpty()) {
                                    viewModel.onTokenPasted(clipboardText)
                                }
                            },
                        )
                        Spacer(Modifier.width(6.dp))
                        TokenFieldIconButton(
                            icon = if (showToken) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = stringResource(
                                if (showToken) R.string.login_hide_token else R.string.login_show_token
                            ),
                            enabled = true,
                            onClick = { showToken = !showToken },
                        )
                    }
                },
                hint = uiState.errorRes?.let { stringResource(it) },
            )

            uiState.warningRes?.let { warningRes ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(warningRes),
                    color = extras.status.pending.content,
                    style = MaterialTheme.typography.labelMedium,
                )
            }

            Spacer(Modifier.height(16.dp))

            PillButton(
                text = if (uiState.isLoading) "…" else stringResource(R.string.login_button),
                onClick = { viewModel.login() },
                enabled = !uiState.isLoading && uiState.token.isNotBlank(),
                variant = PillButtonVariant.Primary,
                fullWidth = true,
                trailing = if (uiState.isLoading) {
                    {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = extras.onAccent,
                            strokeWidth = 2.dp,
                        )
                    }
                } else {
                    {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = extras.onAccent,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                },
            )

            Spacer(Modifier.height(36.dp))

            // Instructions
            DesignCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
                    MonoLabel(text = stringResource(R.string.login_instructions_title))
                    Spacer(Modifier.height(14.dp))
                    val steps = listOf(
                        stringResource(R.string.login_instructions_step1),
                        stringResource(R.string.login_instructions_step2),
                        stringResource(R.string.login_instructions_step3),
                        stringResource(R.string.login_instructions_step4),
                        stringResource(R.string.login_instructions_step5),
                    )
                    steps.forEachIndexed { index, step ->
                        InstructionRow(index = index + 1, text = step)
                    }
                    Spacer(Modifier.height(18.dp))
                    PillButton(
                        text = stringResource(R.string.login_open_token_settings),
                        onClick = {
                            settingsOpenFailed = !openExternalIntent(
                                context,
                                Intent(Intent.ACTION_VIEW, Uri.parse(FastmailLinks.TOKEN_SETTINGS_URL)),
                            )
                        },
                        variant = PillButtonVariant.Secondary,
                    )
                    if (settingsOpenFailed) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.login_open_browser_failed),
                            color = extras.status.pending.content,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            PillButton(
                text = stringResource(R.string.login_try_demo),
                onClick = viewModel::enterDemoMode,
                variant = PillButtonVariant.Ghost,
                enabled = !uiState.isLoading,
                fullWidth = true,
            )
        }
    }
}

/**
 * A compact action inside the token field's trailing slot. Sized to the 28dp
 * touch box the field already used for show/hide so paste sits at the same
 * visual weight rather than competing with the primary button.
 */
@Composable
private fun TokenFieldIconButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val extras = FastMaskExtras.current
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) extras.inkMuted else extras.lineStrong,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun InstructionRow(index: Int, text: String) {
    val extras = FastMaskExtras.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "%02d".format(index),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            color = extras.accent,
            modifier = Modifier
                .width(28.dp)
                .padding(top = 3.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = extras.inkSoft,
        )
    }
}
