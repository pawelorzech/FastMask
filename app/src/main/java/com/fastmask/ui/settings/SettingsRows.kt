package com.fastmask.ui.settings

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.fastmask.ui.accessibility.politeLiveRegion
import com.fastmask.ui.components.expressive.SectionHeader
import com.fastmask.ui.components.expressive.SegmentedGroup
import com.fastmask.ui.components.expressive.SegmentedRow
import com.fastmask.ui.components.expressive.SegmentedSurface

@Composable
internal fun SettingsSection(
    title: String,
    rows: MutableList<@Composable (index: Int, count: Int) -> Unit>.() -> Unit,
) {
    val list = buildList(rows)
    if (list.isEmpty()) return
    SectionHeader(title)
    SegmentedGroup(
        count = list.size,
        modifier = Modifier.padding(horizontal = 16.dp),
    ) { index ->
        list[index](index, list.size)
    }
}

@Composable
internal fun StaticSegment(
    index: Int,
    count: Int,
    content: @Composable RowScope.() -> Unit,
) {
    SegmentedSurface(index = index, count = count, minHeight = 56.dp, content = content)
}

@Composable
internal fun SettingsRow(
    index: Int,
    count: Int,
    label: String,
    leading: @Composable () -> Unit,
    onClick: () -> Unit,
    supporting: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    labelColor: Color = Color.Unspecified,
    showProgress: Boolean = false,
    progressDescription: String? = null,
) {
    SegmentedRow(
        index = index,
        count = count,
        modifier = if (showProgress && progressDescription != null) {
            Modifier
                .politeLiveRegion()
                .semantics { stateDescription = progressDescription }
        } else {
            Modifier
        },
        onClick = onClick,
        leading = leading,
        supporting = supporting?.let { text -> { Text(text) } },
        trailing = if (showProgress) {
            { LoadingIndicator(modifier = Modifier.size(32.dp)) }
        } else {
            trailing
        },
        headline = { Text(text = label, color = labelColor) },
    )
}

/**
 * A clickable row plus an independently-clickable Switch gives TalkBack two focus targets
 * for one setting, so the Switch itself is purely visual (`onCheckedChange = null`).
 */
@Composable
internal fun SettingsToggleRow(
    index: Int,
    count: Int,
    label: String,
    supporting: String,
    leading: @Composable () -> Unit,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    SegmentedRow(
        index = index,
        count = count,
        checked = checked,
        onCheckedChange = { enabled ->
            haptic.performHapticFeedback(
                if (enabled) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
            )
            onCheckedChange(enabled)
        },
        leading = leading,
        supporting = { Text(supporting) },
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                thumbContent = if (checked) {
                    {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize),
                        )
                    }
                } else {
                    null
                },
            )
        },
        headline = { Text(label) },
    )
}

@Composable
internal fun ChevronIcon() {
    // KeyboardArrowRight is AutoMirrored, so RTL (Arabic) points "deeper".
    Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
    )
}
