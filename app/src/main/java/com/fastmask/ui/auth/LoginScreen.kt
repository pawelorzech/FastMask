package com.fastmask.ui.auth

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fastmask.R
import com.fastmask.ui.components.expressive.ExpressiveTextField
import com.fastmask.ui.accessibility.politeLiveRegion
import com.fastmask.ui.accessibility.screenHeading
import com.fastmask.ui.common.FastmailLinks
import com.fastmask.ui.common.openExternalIntent
import com.fastmask.ui.components.LoadingButtonContent
import com.fastmask.ui.theme.FastMaskTheme

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onEnterDemo: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
    onBack: (() -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsState()
    var showToken by rememberSaveable { mutableStateOf(false) }
    var settingsOpenFailed by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is LoginEvent.LoginSuccess -> onLoginSuccess()
                is LoginEvent.EnterDemo -> onEnterDemo()
            }
        }
    }

    LoginContent(
        uiState = uiState,
        showToken = showToken,
        settingsOpenFailed = settingsOpenFailed,
        onTokenChange = viewModel::onTokenChange,
        onToggleShowToken = { showToken = !showToken },
        // Reads the clipboard ONLY from this tap. Never on screen entry or
        // resume — Android 12+ shows a system toast for every clipboard read,
        // and a privacy-first app must not trigger one the user did not ask for.
        // getText() is null for an empty clipboard and for a non-text clip
        // (an image, a URI). Both go to the ViewModel as "" rather than being
        // dropped here, so the tap always produces a visible answer.
        onPaste = { viewModel.onTokenPasted(clipboardManager.getText()?.text.orEmpty()) },
        onLogin = { viewModel.login() },
        onOpenTokenSettings = {
            settingsOpenFailed = !openExternalIntent(
                context,
                // BROWSABLE narrows the candidates to real browsers. A bare
                // ACTION_VIEW https intent matches any component declaring that
                // filter, and the destination is the page where the user is
                // about to type their Fastmail password. openExternalIntent
                // still reports a no-handler case, which the hint covers.
                Intent(Intent.ACTION_VIEW, Uri.parse(FastmailLinks.TOKEN_SETTINGS_URL))
                    .addCategory(Intent.CATEGORY_BROWSABLE),
            )
        },
        onEnterDemo = viewModel::enterDemoMode,
        onBack = onBack ?: { backDispatcher?.onBackPressed(); Unit },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LoginContent(
    uiState: LoginUiState,
    showToken: Boolean,
    settingsOpenFailed: Boolean,
    onTokenChange: (String) -> Unit,
    onToggleShowToken: () -> Unit,
    onPaste: () -> Unit,
    onLogin: () -> Unit,
    onOpenTokenSettings: () -> Unit,
    onEnterDemo: () -> Unit,
    onBack: () -> Unit,
    initiallyExpanded: Boolean = false,
) {
    val haptic = LocalHapticFeedback.current
    val working = stringResource(R.string.state_working)
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val loginBusy = uiState.operation == LoginOperation.LOGIN
    val demoBusy = uiState.operation == LoginOperation.ENTER_DEMO
    val buttonHeight = ButtonDefaults.MediumContainerHeight

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 4.dp, bottom = 32.dp),
        ) {
            // Gaps are padding on each item, not Arrangement.spacedBy: the
            // collapsed AnimatedVisibility items below would still be given a gap.
            LoginHero()

            Text(
                text = stringResource(R.string.login_intro),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 16.dp),
            )

            // Token input. Paste and show/hide both live in the field's trailing
            // slot rather than beside the field, so nothing jumps down at exactly
            // the moment the user reaches for Paste to correct the token.
            ExpressiveTextField(
                value = uiState.token,
                onValueChange = onTokenChange,
                modifier = Modifier.fillMaxWidth().padding(top = 28.dp),
                enabled = !uiState.isLoading,
                label = stringResource(R.string.login_api_token_label),
                placeholder = stringResource(R.string.login_api_token_placeholder),
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                onPaste()
                            },
                            enabled = !uiState.isLoading,
                        ) {
                            Text(stringResource(R.string.login_paste_button))
                        }
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(
                                    if (showToken) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn,
                                )
                                onToggleShowToken()
                            },
                        ) {
                            Icon(
                                imageVector = if (showToken) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = stringResource(
                                    if (showToken) R.string.login_hide_token else R.string.login_show_token,
                                ),
                            )
                        }
                    }
                },
                supportingText = uiState.errorRes?.let { res ->
                    { Text(text = stringResource(res), modifier = Modifier.politeLiveRegion()) }
                },
                isError = uiState.errorRes != null,
                visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onLogin() }),
            )

            // Action shortcut when the missing Masked Email scope error is displayed.
            SpringVisibility(visible = uiState.errorRes == R.string.login_error_missing_masked_email_scope) {
                FilledTonalButton(
                    onClick = onOpenTokenSettings,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    Text(stringResource(R.string.login_error_missing_scope_action))
                }
            }

            // Soft, non-blocking shape hint (never prevents a login attempt).
            val liveWarning = uiState.warningRes
            SpringVisibility(visible = liveWarning != null) {
                // Keeps the last text on screen while the card collapses.
                val warningRes = rememberLast(liveWarning)
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            modifier = Modifier.padding(top = 1.dp),
                        )
                        if (warningRes != null) {
                            Text(
                                text = stringResource(warningRes),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.politeLiveRegion(),
                            )
                        }
                    }
                }
            }

            // Primary action. The label swaps for a loading indicator while the
            // token is validated; the button keeps its size.
            Button(
                onClick = {
                    if (!loginBusy) onLogin()
                },
                enabled = uiState.token.isNotBlank() && (uiState.operation == null || loginBusy),
                shapes = ButtonDefaults.shapesFor(buttonHeight),
                contentPadding = ButtonDefaults.contentPaddingFor(buttonHeight),
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp).heightIn(min = buttonHeight),
            ) {
                LoadingButtonContent(
                    loading = loginBusy,
                    loadingDescription = working,
                    label = stringResource(R.string.login_button),
                    icon = Icons.Filled.Key,
                    iconSize = ButtonDefaults.iconSizeFor(buttonHeight),
                    iconSpacing = ButtonDefaults.iconSpacingFor(buttonHeight),
                )
            }

            HowToCard(
                onOpenTokenSettings = onOpenTokenSettings,
                settingsOpenFailed = settingsOpenFailed,
                initiallyExpanded = initiallyExpanded,
                modifier = Modifier.padding(top = 20.dp),
            )

            TextButton(
                onClick = onEnterDemo,
                enabled = !uiState.isLoading,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                LoadingButtonContent(
                    loading = demoBusy,
                    loadingDescription = working,
                    label = stringResource(R.string.login_try_demo),
                )
            }
        }
    }
}

/**
 * "A quiet place for masked mail." login_hero pre-joins the prefix and accent
 * fragments per locale, replacing a hardcoded single-space join that rendered
 * with no inter-word space in ja/zh and split Arabic's proclitic from the word
 * it must stay attached to. login_hero_accent still carries just the
 * "masked mail" fragment (verbatim, as it appears inside login_hero for that
 * locale) purely so it can be located here and coloured — the join itself never
 * happens in Kotlin.
 */
@Composable
private fun LoginHero() {
    val heroCombined = stringResource(R.string.login_hero)
    val heroAccent = stringResource(R.string.login_hero_accent)
    val heroSuffix = stringResource(R.string.login_hero_suffix)
    val accentColor = MaterialTheme.colorScheme.primary
    val accentStart = heroCombined.indexOf(heroAccent)
    val annotated = buildAnnotatedString {
        if (heroAccent.isNotEmpty() && accentStart >= 0) {
            val accentEnd = accentStart + heroAccent.length
            append(heroCombined.substring(0, accentStart))
            withStyle(SpanStyle(color = accentColor)) {
                append(heroCombined.substring(accentStart, accentEnd))
            }
            append(heroCombined.substring(accentEnd))
        } else {
            // A translation edit broke containment of the accent fragment
            // inside login_hero. Render the whole line plain rather than crash
            // or highlight the wrong text.
            append(heroCombined)
        }
        append(heroSuffix)
    }
    Text(
        text = annotated,
        style = MaterialTheme.typography.headlineLargeEmphasized,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.screenHeading(),
    )
}

/**
 * The "How to get your API token" card. Its corners tighten while the header is
 * pressed, the chevron turns over and the five steps open with the theme's
 * springs.
 */
@Composable
private fun HowToCard(
    onOpenTokenSettings: () -> Unit,
    settingsOpenFailed: Boolean,
    initiallyExpanded: Boolean,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val motion = MaterialTheme.motionScheme
    val corner by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (pressed) 8.dp else 20.dp,
        animationSpec = motion.fastSpatialSpec<Dp>(),
        label = "howToCorner",
    )
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = motion.defaultSpatialSpec<Float>(),
        label = "howToChevron",
    )

    Surface(
        shape = RoundedCornerShape(corner),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clickable(
                        interactionSource = interaction,
                        indication = LocalIndication.current,
                        role = Role.Button,
                        onClick = { expanded = !expanded },
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.login_instructions_title),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Filled.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.graphicsLayer { rotationZ = chevron },
                )
            }

            SpringVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val steps = listOf(
                        stringResource(R.string.login_instructions_step1),
                        stringResource(R.string.login_instructions_step2),
                        stringResource(R.string.login_instructions_step3),
                        stringResource(R.string.login_instructions_step4),
                        stringResource(R.string.login_instructions_step5),
                    )
                    steps.forEachIndexed { index, step ->
                        Row {
                            Text(
                                text = "${index + 1}.",
                                style = MaterialTheme.typography.labelLargeEmphasized,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(28.dp),
                            )
                            Text(
                                text = step,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    TextButton(
                        onClick = onOpenTokenSettings,
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(stringResource(R.string.login_open_token_settings))
                    }
                    if (settingsOpenFailed) {
                        Text(
                            text = stringResource(R.string.login_open_browser_failed),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.politeLiveRegion(),
                        )
                    }
                }
            }
        }
    }
}

/** Expands/collapses [content] with the theme's spatial spring and cross-fades it with the effects spring. */
@Composable
private fun SpringVisibility(visible: Boolean, content: @Composable () -> Unit) {
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(spatial) + fadeIn(effects),
        exit = shrinkVertically(spatial) + fadeOut(effects),
    ) {
        content()
    }
}

/** The most recent non-null [value], so an exit animation can keep drawing what just left. */
@Composable
private fun <T : Any> rememberLast(value: T?): T? {
    var last by remember { mutableStateOf(value) }
    if (value != null && value != last) last = value
    return value ?: last
}

private val previewIdle = LoginUiState()

@Composable
private fun LoginPreviewHost(
    state: LoginUiState,
    showToken: Boolean = false,
    initiallyExpanded: Boolean = false,
    settingsOpenFailed: Boolean = false,
    dark: Boolean = false,
) {
    FastMaskTheme(darkTheme = dark) {
        LoginContent(
            uiState = state,
            showToken = showToken,
            settingsOpenFailed = settingsOpenFailed,
            onTokenChange = {},
            onToggleShowToken = {},
            onPaste = {},
            onLogin = {},
            onOpenTokenSettings = {},
            onEnterDemo = {},
            onBack = {},
            initiallyExpanded = initiallyExpanded,
        )
    }
}

@Preview(name = "Login empty light", showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun LoginEmptyLightPreview() = LoginPreviewHost(previewIdle)

@Preview(name = "Login empty dark", showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun LoginEmptyDarkPreview() = LoginPreviewHost(previewIdle, dark = true)

@Preview(name = "Login shape warning + steps", showBackground = true, widthDp = 360, heightDp = 1000)
@Composable
private fun LoginWarningPreview() = LoginPreviewHost(
    LoginUiState(token = "abcd-not-a-token", warningRes = R.string.login_token_warning_shape),
    showToken = true,
    initiallyExpanded = true,
)

@Preview(name = "Login missing scope error", showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun LoginScopeErrorPreview() = LoginPreviewHost(
    LoginUiState(token = "fmu1-0000", errorRes = R.string.login_error_missing_masked_email_scope),
)

@Preview(name = "Login loading", showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun LoginLoadingPreview() = LoginPreviewHost(
    LoginUiState(token = "fmu1-0000", operation = LoginOperation.LOGIN),
)
