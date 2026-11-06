package com.fastmask.ui.detail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.fastmask.R
import com.fastmask.domain.model.EmailState
import com.fastmask.ui.accessibility.radioButtonGroup
import com.fastmask.ui.components.ButtonLoadingLabel
import com.fastmask.ui.components.MaskStateToggle
import com.fastmask.ui.components.expressive.AdaptiveToggleGroup
import com.fastmask.ui.components.expressive.connectedToggleShapes

@Composable
internal fun StatusControl(
    state: EmailState,
    toggling: Boolean,
    busy: Boolean,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
) {
    val motion = rememberMotion()
    // Optimistic selection: show the tapped option immediately while the request
    // is in flight. When the request fails the in-flight flag drops without the
    // state having changed, which rolls the selection back.
    var pending by remember { mutableStateOf<EmailState?>(null) }
    val shown = if (toggling) pending ?: state else state
    val statusDesc = stringResource(R.string.email_detail_status)

    AnimatedContent(
        targetState = state == EmailState.DELETED,
        transitionSpec = {
            (fadeIn(motion.defaultEffects) +
                scaleIn(motion.defaultSpatial, initialScale = 0.92f)) togetherWith
                fadeOut(motion.fastEffects) using
                SizeTransform(clip = false) { _, _ -> motion.defaultSpatialSize }
        },
        label = "statusControl",
    ) { archived ->
        val height = ButtonDefaults.MediumContainerHeight
        if (archived) {
            FilledTonalButton(
                onClick = {
                    pending = EmailState.ENABLED
                    onEnable()
                },
                enabled = !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = height),
                shapes = ButtonDefaults.shapes(),
                contentPadding = ButtonDefaults.contentPaddingFor(height),
            ) {
                ButtonLoadingLabel(
                    loading = toggling,
                    loadingDescription = stringResource(R.string.state_working),
                    label = stringResource(R.string.email_detail_enable),
                    icon = Icons.Filled.Unarchive,
                    iconSize = ButtonDefaults.iconSizeFor(height),
                    iconSpacing = ButtonDefaults.iconSpacingFor(height),
                    textStyle = MaterialTheme.typography.titleMedium,
                )
            }
        } else {
            val activeSelected = shown == EmailState.ENABLED || shown == EmailState.PENDING
            AdaptiveToggleGroup(
                modifier = Modifier
                    .radioButtonGroup()
                    .semantics { contentDescription = statusDesc },
                first = { toggleModifier, stacked ->
                    MaskStateToggle(
                        label = stringResource(R.string.state_enabled),
                        checked = activeSelected,
                        loading = toggling && activeSelected,
                        shapes = connectedToggleShapes(index = 0, count = 2, vertical = stacked),
                        modifier = toggleModifier,
                        onSelect = {
                            if (!busy) {
                                pending = EmailState.ENABLED
                                onEnable()
                            }
                        },
                    )
                },
                second = { toggleModifier, stacked ->
                    MaskStateToggle(
                        label = stringResource(R.string.state_disabled),
                        checked = !activeSelected,
                        loading = toggling && !activeSelected,
                        shapes = connectedToggleShapes(index = 1, count = 2, vertical = stacked),
                        modifier = toggleModifier,
                        onSelect = {
                            if (!busy) {
                                pending = EmailState.DISABLED
                                onDisable()
                            }
                        },
                    )
                },
            )
        }
    }
}
