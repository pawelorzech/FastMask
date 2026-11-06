package com.fastmask.ui.create

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.hilt.navigation.compose.hiltViewModel
import com.fastmask.R
import com.fastmask.ui.common.ContentWidth
import com.fastmask.ui.common.readableWidth
import com.fastmask.ui.components.rememberDiscardChangesGuard
import com.fastmask.ui.components.MaskStateToggle
import com.fastmask.ui.components.MaskUrlField
import com.fastmask.ui.components.MaskDescriptionField
import com.fastmask.ui.components.MaskDomainField
import com.fastmask.ui.components.MaskField
import com.fastmask.ui.components.expressive.AdaptiveToggleGroup
import com.fastmask.ui.components.expressive.delayInfinite
import com.fastmask.ui.components.expressive.MorphShape
import com.fastmask.ui.components.expressive.rememberMotionReduced
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.fastmask.ui.components.expressive.connectedToggleShapes
import com.fastmask.domain.model.EmailState
import com.fastmask.domain.share.SharePrefill
import com.fastmask.ui.accessibility.politeLiveRegion
import com.fastmask.ui.accessibility.radioButtonGroup
import com.fastmask.ui.accessibility.screenHeading
import com.fastmask.ui.components.ButtonLoadingLabel
import com.fastmask.ui.components.DemoBanner
import com.fastmask.ui.theme.FastMaskTheme

@Composable
fun CreateMaskedEmailScreen(
    onNavigateBack: () -> Unit,
    /** The mask exists; hand its address to the list, which reports it there. */
    onCreated: (String) -> Unit,
    onSignInFromBanner: () -> Unit,
    prefill: SharePrefill? = null,
    viewModel: CreateMaskedEmailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    // Guard against losing typed-but-uncreated input on back / swipe.
    val isDirty = uiState.emailPrefix.isNotEmpty() || uiState.forDomain.isNotEmpty() ||
        uiState.description.isNotEmpty() || uiState.url.isNotEmpty()
    val onClose = rememberDiscardChangesGuard(isDirty, onLeave = onNavigateBack)

    LaunchedEffect(Unit) {
        viewModel.applyPrefill(prefill)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                // Leave immediately. This used to await the confirmation
                // snackbar before navigating, and showSnackbar suspends for its
                // full Long duration (~10 s) unless the action is tapped — so
                // the form sat there looking frozen after a successful create.
                // The list shows the confirmation instead, with the same Copy
                // action, which is also where the new mask now is.
                is CreateMaskedEmailEvent.Created -> onCreated(event.email)
            }
        }
    }

    CreateMaskedEmailContent(
        uiState = uiState,
        banner = { DemoBanner(onSignInClick = onSignInFromBanner) },
        onClose = onClose,
        onPrefixChange = viewModel::onPrefixChange,
        onDomainChange = viewModel::onDomainChange,
        onDescriptionChange = viewModel::onDescriptionChange,
        onUrlChange = viewModel::onUrlChange,
        onStateChange = viewModel::onStateChange,
        onCreate = viewModel::create,
    )
}

@Composable
internal fun CreateMaskedEmailContent(
    uiState: CreateMaskedEmailUiState,
    banner: @Composable () -> Unit,
    onClose: () -> Unit,
    onPrefixChange: (String) -> Unit,
    onDomainChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onUrlChange: (String) -> Unit,
    onStateChange: (EmailState) -> Unit,
    onCreate: () -> Unit,
) {
    val scrollState = rememberScrollState()
    val barColor by animateColorAsState(
        targetValue = if (scrollState.value > 0) {
            MaterialTheme.colorScheme.surfaceContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "barColor",
    )
    val closeDesc = stringResource(R.string.create_email_close)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.create_email_title),
                            modifier = Modifier.screenHeading(),
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onClose, shapes = IconButtonDefaults.shapes()) {
                            Icon(imageVector = Icons.Filled.Close, contentDescription = closeDesc)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = barColor),
                )
                // Demo mode banner (auto-hides in REAL mode). Below the app bar, which already
                // consumed the status-bar inset, so there is no gap above the back arrow.
                banner()
            }
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
                .verticalScroll(scrollState)
                .readableWidth(ContentWidth.Form)
                .padding(horizontal = 16.dp)
                .padding(top = 4.dp, bottom = 32.dp),
        ) {
            PreviewCard(prefix = uiState.emailPrefix)

            Spacer(Modifier.size(18.dp))

            val domainFocus = remember { FocusRequester() }
            val descriptionFocus = remember { FocusRequester() }
            val urlFocus = remember { FocusRequester() }

            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                val prefixError = uiState.prefixErrorRes?.let { stringResource(it) }
                val prefixHint = stringResource(R.string.create_email_prefix_hint)
                MaskField(
                    value = uiState.emailPrefix,
                    onValueChange = onPrefixChange,
                    label = stringResource(R.string.create_email_prefix_label),
                    placeholder = stringResource(R.string.create_email_prefix_placeholder),
                    supporting = prefixError ?: prefixHint,
                    isError = prefixError != null,
                    enabled = !uiState.isLoading,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Next,
                    ),
                    keyboardActions = KeyboardActions(onNext = { domainFocus.requestFocus() }),
                )
                MaskDomainField(
                    value = uiState.forDomain,
                    onValueChange = onDomainChange,
                    label = stringResource(R.string.create_email_domain_label),
                    enabled = !uiState.isLoading,
                    onNext = { descriptionFocus.requestFocus() },
                    modifier = Modifier.focusRequester(domainFocus),
                )
                MaskDescriptionField(
                    value = uiState.description,
                    onValueChange = onDescriptionChange,
                    label = stringResource(R.string.create_email_description_label),
                    enabled = !uiState.isLoading,
                    onNext = { urlFocus.requestFocus() },
                    modifier = Modifier.focusRequester(descriptionFocus),
                )
                MaskUrlField(
                    value = uiState.url,
                    onValueChange = onUrlChange,
                    label = stringResource(R.string.create_email_url_label),
                    enabled = !uiState.isLoading,
                    modifier = Modifier.focusRequester(urlFocus),
                )
            }

            Spacer(Modifier.size(22.dp))

            Text(
                text = stringResource(R.string.create_email_initial_state),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            )
            StateSegmented(
                selected = uiState.initialState,
                onSelect = onStateChange,
                enabled = !uiState.isLoading,
            )

            AnimatedVisibility(
                visible = uiState.errorRes != null,
                enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                    expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()),
                exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                    shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()),
            ) {
                // Keep showing the last message while the exit animation runs.
                val shownError = remember { mutableStateOf(uiState.errorRes) }
                if (uiState.errorRes != null) shownError.value = uiState.errorRes
                CreateError(
                    messageRes = shownError.value,
                    enabled = !uiState.isLoading,
                    onRetry = onCreate,
                    modifier = Modifier.padding(top = 18.dp),
                )
            }

            Spacer(Modifier.size(24.dp))

            val height = ButtonDefaults.MediumContainerHeight
            Button(
                onClick = onCreate,
                enabled = uiState.prefixErrorRes == null && !uiState.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = height),
                shapes = ButtonDefaults.shapes(),
                contentPadding = ButtonDefaults.contentPaddingFor(height),
            ) {
                ButtonLoadingLabel(
                    loading = uiState.isLoading,
                    loadingDescription = stringResource(R.string.state_working),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(ButtonDefaults.iconSpacingFor(height)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.iconSizeFor(height)),
                        )
                        Text(
                            text = stringResource(R.string.create_email_button),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
        }
    }
}

// --- preview card -----------------------------------------------------------

/** Shapes the decorative blob cycles through. */
private val blobShapes = listOf(
    MaterialShapes.Cookie9Sided,
    MaterialShapes.Flower,
    MaterialShapes.Sunny,
    MaterialShapes.Clover4Leaf,
)

@Composable
private fun PreviewCard(prefix: String) {
    val onContainer = MaterialTheme.colorScheme.onPrimaryContainer
    val label = stringResource(R.string.create_email_preview_label)
    val randomText = stringResource(R.string.create_email_preview_random)
    val suffix = stringResource(R.string.create_email_preview_suffix)

    // Slowly morphing decorative blob: hold a shape, then spring into the next.
    val progress = remember { Animatable(1f) }
    var index by remember { mutableStateOf(0) }
    val spatial = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    val reduced = rememberMotionReduced()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(reduced) {
        if (reduced) return@LaunchedEffect
        // Paused while the screen is not RESUMED; the hold goes through the
        // InfiniteAnimationPolicy so tests and idle detection can stop the loop.
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (progress.value != 1f) progress.animateTo(1f, spatial)
            while (true) {
                delayInfinite(2600)
                index = (index + 1) % blobShapes.size
                progress.snapTo(0f)
                progress.animateTo(1f, spatial)
            }
        }
    }
    val morph = remember(index) {
        Morph(
            blobShapes[(index + blobShapes.size - 1) % blobShapes.size],
            blobShapes[index],
        )
    }
    val blobShape = remember(morph) { MorphShape(morph) { progress.value } }

    // Subtle spring "pop" whenever the address changes.
    val pop = remember { Animatable(1f) }
    val popSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    var firstComposition by remember { mutableStateOf(true) }
    LaunchedEffect(prefix) {
        if (firstComposition) {
            firstComposition = false
        } else {
            pop.snapTo(1.025f)
            pop.animateTo(1f, popSpec)
        }
    }

    val address = remember(prefix, onContainer, randomText, suffix) {
        buildAnnotatedString {
            if (prefix.isEmpty()) {
                withStyle(SpanStyle(color = onContainer.copy(alpha = 0.55f), fontStyle = FontStyle.Italic)) {
                    append(randomText)
                }
            } else {
                withStyle(SpanStyle(color = onContainer)) { append(prefix) }
            }
            withStyle(SpanStyle(color = onContainer.copy(alpha = 0.7f))) { append(suffix) }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
    ) {
        // Decorative: no semantics. matchParentSize keeps the blob from stretching the card:
        // the card is exactly as tall as the label and address.
        Box(modifier = Modifier.matchParentSize().clearAndSetSemantics { }) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 40.dp, y = (-44).dp)
                    .size(112.dp)
                    .clip(blobShape)
                    // Low emphasis: it sits behind the address and must never fight it.
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
            )
        }
        Column(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = onContainer.copy(alpha = 0.8f),
            )
            Text(
                text = address,
                style = MaterialTheme.typography.headlineSmallEmphasized.copy(
                    // An address has no spaces: let it break anywhere inside the padding.
                    lineBreak = androidx.compose.ui.text.style.LineBreak.Simple,
                ),
                modifier = Modifier.fillMaxWidth().graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                },
            )
        }
    }
}

// --- form -------------------------------------------------------------------

@Composable
private fun StateSegmented(
    selected: EmailState,
    onSelect: (EmailState) -> Unit,
    enabled: Boolean,
) {
    val haptic = LocalHapticFeedback.current
    AdaptiveToggleGroup(
        modifier = Modifier.radioButtonGroup(),
        first = { toggleModifier, stacked ->
            MaskStateToggle(
                label = stringResource(R.string.state_enabled),
                checked = selected == EmailState.ENABLED,
                enabled = enabled,
                shapes = connectedToggleShapes(index = 0, count = 2, vertical = stacked),
                modifier = toggleModifier,
                onSelect = {
                    haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
                    onSelect(EmailState.ENABLED)
                },
            )
        },
        second = { toggleModifier, stacked ->
            MaskStateToggle(
                label = stringResource(R.string.state_disabled),
                checked = selected == EmailState.DISABLED,
                enabled = enabled,
                shapes = connectedToggleShapes(index = 1, count = 2, vertical = stacked),
                modifier = toggleModifier,
                onSelect = {
                    haptic.performHapticFeedback(HapticFeedbackType.ToggleOff)
                    onSelect(EmailState.DISABLED)
                },
            )
        },
    )
}

@Composable
private fun CreateError(
    messageRes: Int?,
    enabled: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (messageRes == null) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(messageRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier
                .weight(1f)
                .politeLiveRegion(),
        )
        TextButton(
            onClick = onRetry,
            enabled = enabled,
        ) { Text(stringResource(R.string.error_retry)) }
    }
}

// --- previews ---------------------------------------------------------------

@Composable
private fun PreviewHost(uiState: CreateMaskedEmailUiState) {
    FastMaskTheme {
        CreateMaskedEmailContent(
            uiState = uiState,
            banner = {},
            onClose = {},
            onPrefixChange = {},
            onDomainChange = {},
            onDescriptionChange = {},
            onUrlChange = {},
            onStateChange = {},
            onCreate = {},
        )
    }
}

@Preview(name = "Empty", showBackground = true, heightDp = 900)
@Composable
private fun CreateEmptyPreview() = PreviewHost(CreateMaskedEmailUiState())

@Preview(
    name = "Empty dark",
    showBackground = true,
    heightDp = 900,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun CreateEmptyDarkPreview() = PreviewHost(CreateMaskedEmailUiState())

@Preview(name = "Filled", showBackground = true, heightDp = 900)
@Composable
private fun CreateFilledPreview() = PreviewHost(
    CreateMaskedEmailUiState(
        emailPrefix = "mysite_shopping",
        forDomain = "figma.com",
        description = "Design tool account",
        url = "https://figma.com",
        initialState = EmailState.DISABLED,
    ),
)

@Preview(name = "Prefix error", showBackground = true, heightDp = 900)
@Composable
private fun CreatePrefixErrorPreview() = PreviewHost(
    CreateMaskedEmailUiState(
        emailPrefix = "bad",
        prefixErrorRes = R.string.create_email_error_prefix_chars,
    ),
)

@Preview(name = "Creating", showBackground = true, heightDp = 900)
@Composable
private fun CreateLoadingPreview() = PreviewHost(
    CreateMaskedEmailUiState(emailPrefix = "mysite", isLoading = true),
)

@Preview(name = "Create failed", showBackground = true, heightDp = 900)
@Composable
private fun CreateErrorPreview() = PreviewHost(
    CreateMaskedEmailUiState(emailPrefix = "mysite", errorRes = R.string.create_email_error_failed),
)
