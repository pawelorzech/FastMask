package com.fastmask.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.fastmask.R
import androidx.compose.foundation.selection.selectableGroup
import com.fastmask.ui.components.expressive.AdaptiveToggleGroup
import com.fastmask.ui.components.expressive.ExpressiveTextField
import com.fastmask.ui.components.expressive.connectedToggleShapes

/*
 * The pieces the create form and the detail editor share. Labels stay
 * parameters: each screen has its own string resources, and the screens differ
 * in field order, so the focus chain is wired at the call site via `onNext`.
 */

/**
 * One text field of the mask form.
 *
 * The label is part of the field; naming the node explicitly keeps it
 * addressable by label (TalkBack, instrumented tests), and an invalid entry
 * puts its message on the field's own node.
 */
@Composable
fun MaskField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    enabled: Boolean,
    keyboardOptions: KeyboardOptions,
    modifier: Modifier = Modifier,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    supporting: String? = null,
    isError: Boolean = false,
) {
    ExpressiveTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        isError = isError,
        label = label,
        placeholder = placeholder,
        supportingText = supporting?.let { text -> { Text(text) } },
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = label
                if (isError && supporting != null) error(supporting)
            },
    )
}

@Composable
fun MaskDescriptionField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) = MaskField(
    value = value,
    onValueChange = onValueChange,
    label = label,
    placeholder = stringResource(R.string.create_email_description_placeholder),
    enabled = enabled,
    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
    keyboardActions = KeyboardActions(onNext = { onNext() }),
    modifier = modifier,
)

@Composable
fun MaskDomainField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) = MaskField(
    value = value,
    onValueChange = onValueChange,
    label = label,
    placeholder = stringResource(R.string.create_email_domain_placeholder),
    enabled = enabled,
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
    keyboardActions = KeyboardActions(onNext = { onNext() }),
    modifier = modifier,
)

/** The URL field; always the last in the chain, so the IME action is "Done". */
@Composable
fun MaskUrlField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) = MaskField(
    value = value,
    onValueChange = onValueChange,
    label = label,
    placeholder = stringResource(R.string.create_email_url_placeholder),
    enabled = enabled,
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
    modifier = modifier,
)

/**
 * One half of the two-option Enabled / Disabled toggle. Exactly one option is
 * always chosen, so it is a radio choice for screen readers rather than two
 * independent toggles; [onSelect] fires only when an unchosen option is tapped.
 *
 * @param loading shows a spinner in place of the check while the change this
 *   option represents is being applied (detail screen only).
 */
@Composable
private fun MaskStateToggle(
    label: String,
    checked: Boolean,
    shapes: ToggleButtonShapes,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val height = ButtonDefaults.MediumContainerHeight
    ToggleButton(
        checked = checked,
        onCheckedChange = { if (it && !checked) onSelect() },
        enabled = enabled,
        modifier = modifier
            .heightIn(min = height)
            .semantics { role = Role.RadioButton },
        shapes = shapes,
        contentPadding = ToggleButtonDefaults.contentPaddingFor(height),
    ) {
        AnimatedVisibility(
            visible = checked,
            enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()) +
                expandHorizontally(MaterialTheme.motionScheme.fastSpatialSpec()),
            exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                shrinkHorizontally(MaterialTheme.motionScheme.fastSpatialSpec()),
        ) {
            Box(modifier = Modifier.padding(end = ToggleButtonDefaults.IconSpacing)) {
                if (loading) {
                    LoadingIndicator(modifier = Modifier.size(ToggleButtonDefaults.IconSize))
                } else {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(ToggleButtonDefaults.IconSize),
                    )
                }
            }
        }
        Text(text = label, style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * The Enabled / Disabled choice shared by the create form and the detail
 * screen: two connected [MaskStateToggle]s that read as one radio group, with
 * the toggle haptic on a selection.
 *
 * @param activeSelected true when "Enabled" is the chosen option (see
 *   `EmailState.isActive`); "Disabled" is chosen otherwise.
 * @param enabled false dims and disables both options (create form while saving).
 * @param busy true ignores taps (no callback, no haptic) without dimming, for a
 *   change already in flight (detail screen).
 * @param loading shows the spinner on whichever option is chosen.
 */
@Composable
fun EnabledDisabledToggle(
    activeSelected: Boolean,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
    loading: Boolean = false,
) {
    val haptic = LocalHapticFeedback.current
    AdaptiveToggleGroup(
        modifier = modifier.selectableGroup(),
        first = { toggleModifier, stacked ->
            MaskStateToggle(
                label = stringResource(R.string.state_enabled),
                checked = activeSelected,
                loading = loading && activeSelected,
                enabled = enabled,
                shapes = connectedToggleShapes(index = 0, count = 2, vertical = stacked),
                modifier = toggleModifier,
                onSelect = {
                    if (!busy) {
                        haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
                        onEnable()
                    }
                },
            )
        },
        second = { toggleModifier, stacked ->
            MaskStateToggle(
                label = stringResource(R.string.state_disabled),
                checked = !activeSelected,
                loading = loading && !activeSelected,
                enabled = enabled,
                shapes = connectedToggleShapes(index = 1, count = 2, vertical = stacked),
                modifier = toggleModifier,
                onSelect = {
                    if (!busy) {
                        haptic.performHapticFeedback(HapticFeedbackType.ToggleOff)
                        onDisable()
                    }
                },
            )
        },
    )
}

/**
 * Guards a form against losing typed-but-unsaved input: while [isDirty], the
 * system back gesture opens the discard dialog, and so does the returned
 * request-leave callback (wire it to the close / back button). Confirming
 * calls [onLeave]. When the form is clean the callback leaves immediately.
 */
@Composable
fun rememberDiscardChangesGuard(isDirty: Boolean, onLeave: () -> Unit): () -> Unit {
    var showDialog by remember { mutableStateOf(false) }
    BackHandler(enabled = isDirty) { showDialog = true }
    if (showDialog) {
        ConfirmDialog(
            title = stringResource(R.string.discard_changes_title),
            message = stringResource(R.string.discard_changes_message),
            confirmText = stringResource(R.string.discard_changes_confirm),
            dismissText = stringResource(R.string.discard_changes_cancel),
            onConfirm = { showDialog = false; onLeave() },
            onDismiss = { showDialog = false },
        )
    }
    return { if (isDirty) showDialog = true else onLeave() }
}
