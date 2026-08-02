package com.fastmask.ui.accessibility

import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics

/** Marks the visual title of a screen for heading navigation. */
fun Modifier.screenHeading(): Modifier = semantics { heading() }

/** Gives a set of radio-button items their missing group relationship. */
fun Modifier.radioButtonGroup(): Modifier = selectableGroup()

/** Announces text changes without interrupting the current screen-reader utterance. */
fun Modifier.politeLiveRegion(): Modifier = semantics {
    liveRegion = LiveRegionMode.Polite
}
