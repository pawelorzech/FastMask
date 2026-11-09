package com.fastmask.ui.detail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.fastmask.R
import com.fastmask.domain.model.EmailState
import com.fastmask.ui.components.ButtonLoadingLabel
import com.fastmask.ui.components.EnabledDisabledToggle

@Composable
internal fun StatusControl(
    state: EmailState,
    toggling: Boolean,
    busy: Boolean,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
) {
    val motion = rememberMotion()
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
            EnabledDisabledToggle(
                activeSelected = shown.isActive,
                loading = toggling,
                busy = busy,
                onEnable = {
                    pending = EmailState.ENABLED
                    onEnable()
                },
                onDisable = {
                    pending = EmailState.DISABLED
                    onDisable()
                },
                modifier = Modifier.semantics { contentDescription = statusDesc },
            )
        }
    }
}
