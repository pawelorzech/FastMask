package com.fastmask.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue

/**
 * The top bar of the create and detail screens: flat on the surface, then it
 * takes the `surfaceContainer` tone once the content has scrolled under it
 * ([raised]). [banner] sits below the bar, which already consumed the
 * status-bar inset, so the demo banner leaves no gap above the back arrow.
 */
@Composable
internal fun ScrollRaisedTopBar(
    raised: Boolean,
    title: @Composable () -> Unit,
    navigationIcon: @Composable () -> Unit,
    banner: @Composable () -> Unit,
) {
    val barColor by animateColorAsState(
        targetValue = if (raised) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "barColor",
    )
    Column {
        TopAppBar(
            title = title,
            navigationIcon = navigationIcon,
            colors = TopAppBarDefaults.topAppBarColors(containerColor = barColor),
        )
        // Demo mode banner (auto-hides in REAL mode).
        banner()
    }
}
