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
import androidx.compose.foundation.layout.requiredSize
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
import androidx.compose.ui.semantics.Role
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
import com.fastmask.ui.accessibility.politeLiveRegion
import com.fastmask.ui.accessibility.screenHeading
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

            // Hero — "A quiet place for masked mail". login_hero pre-joins the
            // prefix and accent fragments per locale, replacing a hardcoded
            // single-space join that rendered with no inter-word space in
            // ja/zh and split Arabic's لـ proclitic from the word it must
            // stay attached to. login_hero_accent still carries just the
            // "masked mail" fragment (verbatim, as it appears inside
            // login_hero for that locale) purely so it can be located here
            // and given its accent colour + italic — the join itself never
            // happens in Kotlin.
            val heroCombined = stringResource(R.string.login_hero)
            val heroAccent = stringResource(R.string.login_hero_accent)
            val heroSuffix = stringResource(R.string.login_hero_suffix)
            val baseStyle = SpanStyle(
                color = MaterialTheme.colorScheme.onBackground,
                fontFamily = InstrumentSerif,
            )
            val accentStyle = SpanStyle(
                color = extras.accent,
                fontStyle = FontStyle.Italic,
                fontFamily = InstrumentSerif,
            )
            val accentStart = heroCombined.indexOf(heroAccent)
            val annotated = buildAnnotatedString {
                if (heroAccent.isNotEmpty() && accentStart >= 0) {
                    val accentEnd = accentStart + heroAccent.length
                    withStyle(baseStyle) { append(heroCombined.substring(0, accentStart)) }
                    withStyle(accentStyle) { append(heroCombined.substring(accentStart, accentEnd)) }
                    withStyle(baseStyle) { append(heroCombined.substring(accentEnd)) }
                } else {
                    // Fallback: a translation edit broke containment of the
                    // accent fragment inside login_hero. Render the whole
                    // line in the base style rather than crash or highlight
                    // the wrong text.
                    withStyle(baseStyle) { append(heroCombined) }
                }
                withStyle(baseStyle) { append(heroSuffix) }
            }
            Text(
                text = annotated,
                style = MaterialTheme.typography.displayLarge,
                modifier = Modifier.screenHeading(),
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
                                // getText() is null for an empty clipboard and
                                // for a non-text clip (an image, a URI). Both
                                // go to the ViewModel as "" rather than being
                                // dropped here, so the tap always produces a
                                // visible answer.
                                viewModel.onTokenPasted(clipboardManager.getText()?.text.orEmpty())
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
                    modifier = Modifier.politeLiveRegion(),
                )
            }

            Spacer(Modifier.height(16.dp))

            PillButton(
                text = stringResource(R.string.login_button),
                // `loading`, not a hand-rolled spinner in `trailing`: the flag is
                // what drives the screen-reader announcement, and it also
                // disables the button, so the enabled expression no longer has
                // to repeat !isLoading.
                loading = uiState.isLoading,
                loadingDescription = stringResource(R.string.state_working),
                onClick = { viewModel.login() },
                enabled = uiState.token.isNotBlank(),
                variant = PillButtonVariant.Primary,
                fullWidth = true,
                trailing = if (uiState.isLoading) {
                    null
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
                                // BROWSABLE narrows the candidates to real
                                // browsers. A bare ACTION_VIEW https intent
                                // matches any component declaring that filter,
                                // and the destination is the page where the
                                // user is about to type their Fastmail
                                // password. openExternalIntent still reports a
                                // no-handler case, which the hint below covers.
                                Intent(Intent.ACTION_VIEW, Uri.parse(FastmailLinks.TOKEN_SETTINGS_URL))
                                    .addCategory(Intent.CATEGORY_BROWSABLE),
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
                            modifier = Modifier.politeLiveRegion(),
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

/** Material's minimum touch target. Two of these sit side by side in a field. */
private val TokenFieldTouchTarget = 48.dp

/**
 * Vertical footprint the field reserves for them — the visual weight the
 * trailing slot had before the touch target was widened.
 */
private val TokenFieldSlotHeight = 28.dp

/**
 * A compact action inside the token field's trailing slot: a 20dp icon with a
 * full 48dp touch target.
 *
 * The target is honest, not decorative. Aiming for Paste and hitting Show
 * instead reveals the token in plaintext on a screen whose threat model is
 * literally the person standing behind you, so these two must be hard to
 * confuse. `requiredSize` inside a shorter parent buys the height for it
 * without inflating the field: the touch box overflows into the input's own
 * 14dp vertical padding, which is empty space, while the slot keeps reporting
 * its original height upwards. Applying `minimumInteractiveComponentSize()`
 * instead would push the field from 56dp to 76dp.
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
        modifier = Modifier.size(width = TokenFieldTouchTarget, height = TokenFieldSlotHeight),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .requiredSize(TokenFieldTouchTarget)
                .clip(CircleShape)
                .clickable(
                    enabled = enabled,
                    // Announced as a button by TalkBack; without it both icons
                    // read as unlabeled clickable regions.
                    role = Role.Button,
                    onClick = onClick,
                ),
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
