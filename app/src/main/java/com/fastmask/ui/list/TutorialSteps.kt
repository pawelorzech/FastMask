package com.fastmask.ui.list

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.res.stringResource
import com.fastmask.ui.components.TutorialStep

@Composable
internal fun rememberTutorialSteps(bounds: Map<TutorialTarget, Rect>): List<TutorialStep> =
    TutorialTarget.entries.map { target ->
        TutorialStep(
            title = stringResource(target.titleRes),
            description = stringResource(target.bodyRes),
            targetBounds = bounds[target],
        )
    }
