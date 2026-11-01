package com.fastmask.ui.welcome

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fastmask.R
import com.fastmask.ui.accessibility.politeLiveRegion
import com.fastmask.ui.accessibility.screenHeading
import com.fastmask.ui.components.LoadingButtonContent
import com.fastmask.ui.theme.FastMaskTheme
import kotlinx.coroutines.delay

@Composable
fun WelcomeScreen(
    onSignIn: () -> Unit,
    onEnterDemo: () -> Unit,
    viewModel: WelcomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is WelcomeEvent.EnterDemo -> onEnterDemo()
                is WelcomeEvent.GoToSignIn -> onSignIn()
            }
        }
    }

    WelcomeContent(
        isLoading = uiState.isLoading,
        errorRes = uiState.errorRes,
        onSignIn = viewModel::goToSignIn,
        onEnterDemo = viewModel::enterDemoMode,
    )
}

@Composable
internal fun WelcomeContent(
    isLoading: Boolean,
    errorRes: Int?,
    onSignIn: () -> Unit,
    onEnterDemo: () -> Unit,
) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            // The hero takes whatever height the copy leaves over, so a tall
            // phone gets a big composition and a short window just a small one.
            WelcomeHero(modifier = Modifier.weight(1f).fillMaxWidth())

            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.displayLargeEmphasized,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.screenHeading(),
                )
                Text(
                    text = stringResource(R.string.welcome_tagline),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )

                val height = ButtonDefaults.MediumContainerHeight
                val working = stringResource(R.string.state_working)

                Button(
                    onClick = onSignIn,
                    enabled = !isLoading,
                    shapes = ButtonDefaults.shapesFor(height),
                    contentPadding = ButtonDefaults.contentPaddingFor(height),
                    modifier = Modifier.fillMaxWidth().heightIn(min = height),
                ) {
                    LoadingButtonContent(
                        loading = false,
                        loadingDescription = working,
                        label = stringResource(R.string.welcome_signin_cta),
                        icon = Icons.AutoMirrored.Filled.Login,
                        iconSize = ButtonDefaults.iconSizeFor(height),
                        iconSpacing = ButtonDefaults.iconSpacingFor(height),
                    )
                }

                FilledTonalButton(
                    onClick = onEnterDemo,
                    shapes = ButtonDefaults.shapesFor(height),
                    contentPadding = ButtonDefaults.contentPaddingFor(height),
                    modifier = Modifier.fillMaxWidth().heightIn(min = height),
                ) {
                    LoadingButtonContent(
                        loading = isLoading,
                        loadingDescription = working,
                        label = stringResource(R.string.welcome_demo_cta),
                        icon = Icons.Filled.PlayArrow,
                        iconSize = ButtonDefaults.iconSizeFor(height),
                        iconSpacing = ButtonDefaults.iconSpacingFor(height),
                    )
                }

                errorRes?.let { res ->
                    Text(
                        text = stringResource(res),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.politeLiveRegion(),
                    )
                }

                Text(
                    text = stringResource(R.string.welcome_privacy_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        }
    }
}

/**
 * Three shapes that keep changing into other shapes: a big primaryContainer
 * one carrying the @ glyph, a tertiaryContainer one tucked top-right and a small
 * primary one bottom-left. Each cycles through its own sequence, staggered and
 * turning slightly. Purely decorative, so it exposes nothing to accessibility.
 */
@Composable
private fun WelcomeHero(modifier: Modifier = Modifier) {
    val big = remember {
        listOf(
            MaterialShapes.Cookie9Sided,
            MaterialShapes.Clover4Leaf,
            MaterialShapes.Sunny,
            MaterialShapes.Cookie4Sided,
            MaterialShapes.Gem,
        )
    }
    val medium = remember {
        listOf(
            MaterialShapes.Flower,
            MaterialShapes.Circle,
            MaterialShapes.Triangle,
            MaterialShapes.Burst,
        )
    }
    val small = remember {
        listOf(
            MaterialShapes.Pentagon,
            MaterialShapes.Square,
            MaterialShapes.Cookie6Sided,
            MaterialShapes.Oval,
        )
    }

    BoxWithConstraints(
        modifier = modifier.clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        val side = minOf(maxWidth * 0.78f, maxHeight * 0.92f, 300.dp)
        Box(Modifier.size(side)) {
            MorphingShape(
                polygons = big,
                color = MaterialTheme.colorScheme.primaryContainer,
                rotationStepDegrees = 45f,
                modifier = Modifier
                    .fillMaxSize()
                    .popIn(delayMillis = 0),
            ) {
                Icon(
                    imageVector = Icons.Filled.AlternateEmail,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(side * 0.32f),
                )
            }
            MorphingShape(
                polygons = medium,
                color = MaterialTheme.colorScheme.tertiaryContainer,
                startDelayMillis = 800L,
                rotationStepDegrees = -60f,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = side * 0.04f, y = -side * 0.02f)
                    .size(side * 0.44f)
                    .popIn(delayMillis = 160),
            )
            MorphingShape(
                polygons = small,
                color = MaterialTheme.colorScheme.primary,
                startDelayMillis = 1600L,
                rotationStepDegrees = 90f,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = side * 0.02f, y = -side * 0.04f)
                    .size(side * 0.30f)
                    .popIn(delayMillis = 320),
            )
        }
    }
}

/** Springs the element in from 60% size and transparent. Shown at rest in previews or with animations off. */
@Composable
private fun Modifier.popIn(delayMillis: Int): Modifier {
    val settled = LocalInspectionMode.current || rememberMotionReduced()
    val progress = remember { Animatable(if (settled) 1f else 0f) }
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(settled) {
        if (!settled) {
            delay(delayMillis.toLong())
            progress.animateTo(1f, spec)
        }
    }
    return graphicsLayer {
        val p = progress.value
        val scale = 0.6f + 0.4f * p
        scaleX = scale
        scaleY = scale
        alpha = p.coerceIn(0f, 1f)
    }
}

@Preview(name = "Welcome light", showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun WelcomeLightPreview() {
    FastMaskTheme(darkTheme = false) {
        WelcomeContent(isLoading = false, errorRes = null, onSignIn = {}, onEnterDemo = {})
    }
}

@Preview(name = "Welcome dark", showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun WelcomeDarkPreview() {
    FastMaskTheme(darkTheme = true) {
        WelcomeContent(isLoading = false, errorRes = null, onSignIn = {}, onEnterDemo = {})
    }
}

@Preview(name = "Welcome loading + error", showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun WelcomeLoadingPreview() {
    FastMaskTheme(darkTheme = false) {
        WelcomeContent(
            isLoading = true,
            errorRes = R.string.demo_error_start,
            onSignIn = {},
            onEnterDemo = {},
        )
    }
}
