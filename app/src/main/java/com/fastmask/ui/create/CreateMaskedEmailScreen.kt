package com.fastmask.ui.create

import androidx.compose.animation.AnimatedVisibility
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.fastmask.R
import com.fastmask.ui.common.ContentWidth
import com.fastmask.ui.common.readableWidth
import com.fastmask.ui.components.rememberDiscardChangesGuard
import com.fastmask.ui.components.MaskUrlField
import com.fastmask.ui.components.MaskDescriptionField
import com.fastmask.ui.components.MaskDomainField
import com.fastmask.ui.components.MaskField
import com.fastmask.ui.components.expressive.MorphingShape
import com.fastmask.domain.model.EmailState
import com.fastmask.ui.accessibility.politeLiveRegion
import com.fastmask.ui.accessibility.screenHeading
import com.fastmask.ui.components.ButtonLoadingLabel
import com.fastmask.ui.components.DemoBanner
import com.fastmask.ui.components.EnabledDisabledToggle
import com.fastmask.ui.components.InlineErrorBanner
import com.fastmask.ui.components.ScrollRaisedTopBar
import com.fastmask.ui.common.UiError
import com.fastmask.ui.common.UiErrorAction
import com.fastmask.ui.theme.FastMaskTheme

@Composable
fun CreateMaskedEmailScreen(
    onNavigateBack: () -> Unit,
    onCreated: (String) -> Unit,
    onSignInFromBanner: () -> Unit,
    onReauthenticate: () -> Unit,
    viewModel: CreateMaskedEmailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    val onClose = rememberDiscardChangesGuard(uiState.isDirty, onLeave = onNavigateBack)

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                // Leave immediately: awaiting showSnackbar here would block navigation
                // for its full Long duration (~10 s).
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
        onReauthenticate = onReauthenticate,
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
    onReauthenticate: () -> Unit,
) {
    val scrollState = rememberScrollState()
    val closeDesc = stringResource(R.string.action_close)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            ScrollRaisedTopBar(
                raised = scrollState.value > 0,
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
                banner = banner,
            )
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
            EnabledDisabledToggle(
                activeSelected = uiState.initialState.isActive,
                onEnable = { onStateChange(EmailState.ENABLED) },
                onDisable = { onStateChange(EmailState.DISABLED) },
                enabled = !uiState.isLoading,
            )

            AnimatedVisibility(
                visible = uiState.error != null,
                enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                    expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()),
                exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                    shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()),
            ) {
                // Keep showing the last message while the exit animation runs.
                val shownError = remember { mutableStateOf(uiState.error) }
                if (uiState.error != null) shownError.value = uiState.error
                shownError.value?.let { error ->
                    InlineErrorBanner(
                        error = error,
                        onRetry = onCreate,
                        onReauthenticate = onReauthenticate,
                        actionEnabled = !uiState.isLoading,
                        modifier = Modifier.padding(top = 18.dp),
                    )
                }
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
                    label = stringResource(R.string.create_email_button),
                    icon = Icons.Filled.Add,
                    iconSize = ButtonDefaults.iconSizeFor(height),
                    iconSpacing = ButtonDefaults.iconSpacingFor(height),
                    textStyle = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

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
            // Slowly morphing: holds a shape, then springs into the next. Low
            // emphasis: it sits behind the address and must never fight it.
            MorphingShape(
                polygons = blobShapes,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                holdMillis = 2600L,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 40.dp, y = (-44).dp)
                    .size(112.dp),
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
            onReauthenticate = {},
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
    CreateMaskedEmailUiState(
        emailPrefix = "mysite",
        error = UiError(R.string.error_create_mask, UiErrorAction.NONE),
    ),
)
