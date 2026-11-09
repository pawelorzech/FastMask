package com.fastmask.ui.accessibility

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics

fun Modifier.screenHeading(): Modifier = semantics { heading() }

/** Announces text changes without interrupting the current screen-reader utterance. */
fun Modifier.politeLiveRegion(): Modifier = semantics {
    liveRegion = LiveRegionMode.Polite
}
