package com.fastmask.ui.pro

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.graphics.shapes.RoundedPolygon
import com.fastmask.R
import com.fastmask.domain.model.ProStatus
import com.fastmask.domain.repository.ProProduct
import com.fastmask.ui.accessibility.politeLiveRegion
import com.fastmask.ui.common.openExternalIntent
import com.fastmask.ui.components.expressive.SegmentedGroup
import com.fastmask.ui.components.expressive.SegmentedRow
import com.fastmask.ui.theme.FastMaskTheme
import kotlinx.coroutines.launch

private const val PRIVACY_URL = "https://pawelorzech.github.io/FastMask/privacy.html"
private const val TERMS_URL = "https://pawelorzech.github.io/FastMask/terms.html"

@Composable
fun ProScreen(
    onNavigateBack: () -> Unit,
    viewModel: ProViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }

    // The hero bursts only for a purchase that happens while this screen is
    // open, not when an owner simply opens it.
    val alreadyPro = remember { uiState.status == ProStatus.PRO }
    var celebrate by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.status) {
        if (uiState.status == ProStatus.PRO && !alreadyPro) celebrate = true
    }

    val messages = ProMessages(
        purchaseCompleted = stringResource(R.string.pro_purchase_success),
        purchasePending = stringResource(R.string.pro_purchase_pending),
        purchaseCancelled = stringResource(R.string.pro_purchase_cancelled),
        purchaseFailed = stringResource(R.string.pro_purchase_failed),
        billingUnavailable = stringResource(R.string.pro_billing_unavailable),
        restored = stringResource(R.string.pro_restore_success),
        nothingToRestore = stringResource(R.string.pro_restore_nothing),
        restoreFailed = stringResource(R.string.pro_restore_failed),
    )

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                ProUiEvent.PurchaseCompleted, ProUiEvent.Restored ->
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                ProUiEvent.PurchaseFailed, ProUiEvent.RestoreFailed, ProUiEvent.BillingUnavailable ->
                    haptic.performHapticFeedback(HapticFeedbackType.Reject)
                else -> Unit
            }
            snackbarHostState.showSnackbar(
                when (event) {
                    ProUiEvent.PurchaseCompleted -> messages.purchaseCompleted
                    ProUiEvent.PurchasePending -> messages.purchasePending
                    ProUiEvent.PurchaseCancelled -> messages.purchaseCancelled
                    ProUiEvent.PurchaseFailed -> messages.purchaseFailed
                    ProUiEvent.BillingUnavailable -> messages.billingUnavailable
                    ProUiEvent.Restored -> messages.restored
                    ProUiEvent.NothingToRestore -> messages.nothingToRestore
                    ProUiEvent.RestoreFailed -> messages.restoreFailed
                }
            )
        }
    }

    // Play requires these legal links to work. If no browser can take the
    // intent the user must be told — the previous resolveActivity() guard let
    // the tap fail in complete silence.
    val noAppMessage = stringResource(R.string.error_no_app_for_link)
    val scope = rememberCoroutineScope()
    val openLink: (String) -> Unit = { url ->
        val opened = openExternalIntent(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        if (!opened) {
            scope.launch { snackbarHostState.showSnackbar(noAppMessage) }
        }
    }

    ProContent(
        state = uiState,
        snackbarHostState = snackbarHostState,
        celebrate = celebrate,
        // PAYWALL_CLOSED is tracked in the ViewModel's onCleared so
        // gesture/system back counts too, not just this button.
        onClose = onNavigateBack,
        onBuy = { activity?.let(viewModel::buy) },
        onRetry = viewModel::loadProduct,
        onRestore = viewModel::restore,
        onOpenLink = openLink,
    )
}

/** What the purchase area below the features shows. */
private enum class PurchaseArea { OWNED, PENDING, LOADING, UNAVAILABLE, READY }

@Composable
internal fun ProContent(
    state: ProUiState,
    snackbarHostState: SnackbarHostState,
    celebrate: Boolean,
    onClose: () -> Unit,
    onBuy: () -> Unit,
    onRetry: () -> Unit,
    onRestore: () -> Unit,
    onOpenLink: (String) -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val owned = state.status == ProStatus.PRO
    val scroll = rememberScrollState()
    val closeDesc = stringResource(R.string.pro_close)
    val area = when {
        owned -> PurchaseArea.OWNED
        state.status == ProStatus.PENDING -> PurchaseArea.PENDING
        state.productState == ProductState.LOADING -> PurchaseArea.LOADING
        state.productState == ProductState.UNAVAILABLE -> PurchaseArea.UNAVAILABLE
        else -> PurchaseArea.READY
    }

    // The bar title fades in once the hero has scrolled away.
    val showBarTitle = scroll.value > 220
    val barTitleAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (showBarTitle) 1f else 0f,
        animationSpec = motion.defaultEffectsSpec(),
        label = "proBarTitle",
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.pro_title),
                        modifier = Modifier.alpha(barTitleAlpha),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose, shapes = IconButtonDefaults.shapes()) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = closeDesc,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ProHero(celebrate = celebrate)
            Spacer(Modifier.height(8.dp))

            AnimatedContent(
                targetState = owned,
                transitionSpec = {
                    fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec())
                },
                label = "proHeadline",
            ) { isOwned ->
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(if (isOwned) R.string.pro_owned else R.string.pro_title),
                        style = MaterialTheme.typography.displaySmallEmphasized,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .semantics { heading() }
                            .then(if (isOwned) Modifier.politeLiveRegion() else Modifier),
                    )
                    if (!isOwned) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.pro_subtitle),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            val features = listOf(
                ProFeature(
                    icon = Icons.Filled.Palette,
                    shape = MaterialShapes.Cookie4Sided,
                    title = stringResource(R.string.pro_feature_accents_title),
                    body = stringResource(R.string.pro_feature_accents_body),
                ),
                ProFeature(
                    icon = Icons.Filled.Fingerprint,
                    shape = MaterialShapes.Clover4Leaf,
                    title = stringResource(R.string.pro_feature_lock_title),
                    body = stringResource(R.string.pro_feature_lock_body),
                ),
                ProFeature(
                    icon = Icons.Filled.FileDownload,
                    shape = MaterialShapes.Pentagon,
                    title = stringResource(R.string.pro_feature_export_title),
                    body = stringResource(R.string.pro_feature_export_body),
                ),
            )
            SegmentedGroup(count = features.size) { index ->
                val feature = features[index]
                SegmentedRow(
                    index = index,
                    count = features.size,
                    leading = { ShapeIcon(feature.shape, feature.icon) },
                    supporting = { Text(feature.body) },
                    headline = { Text(feature.title) },
                )
            }

            Spacer(Modifier.height(24.dp))

            AnimatedContent(
                targetState = area,
                transitionSpec = {
                    (fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), initialScale = 0.92f)) togetherWith
                        (fadeOut(motion.fastEffectsSpec()) + scaleOut(motion.fastSpatialSpec(), targetScale = 0.92f))
                },
                label = "proPurchaseArea",
            ) { current ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    when (current) {
                        PurchaseArea.OWNED -> FilledTonalButton(
                            onClick = onClose,
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = ButtonDefaults.LargeContainerHeight),
                            contentPadding = ButtonDefaults.LargeContentPadding,
                        ) {
                            Icon(imageVector = Icons.Filled.Check, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text(
                                text = stringResource(R.string.pro_done),
                                style = MaterialTheme.typography.titleMediumEmphasized,
                            )
                        }

                        PurchaseArea.PENDING -> InlineNotice(
                            text = stringResource(R.string.pro_purchase_pending),
                            container = MaterialTheme.colorScheme.tertiaryContainer,
                            content = MaterialTheme.colorScheme.onTertiaryContainer,
                            busy = true,
                        )

                        PurchaseArea.LOADING -> Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(ButtonDefaults.LargeContainerHeight),
                            contentAlignment = Alignment.Center,
                        ) {
                            ContainedLoadingIndicator()
                        }

                        PurchaseArea.UNAVAILABLE -> {
                            InlineNotice(
                                text = stringResource(R.string.pro_billing_unavailable),
                                container = MaterialTheme.colorScheme.errorContainer,
                                content = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Spacer(Modifier.height(12.dp))
                            FilledTonalButton(
                                onClick = onRetry,
                                shapes = ButtonDefaults.shapes(),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.pro_retry))
                            }
                        }

                        PurchaseArea.READY -> {
                            BuyButton(
                                price = state.product?.formattedPrice.orEmpty(),
                                inFlight = state.purchaseInFlight,
                                onClick = onBuy,
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = stringResource(R.string.pro_one_time_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }

            if (!owned) {
                Spacer(Modifier.height(8.dp))
                RestoreButton(inFlight = state.restoreInFlight, onClick = onRestore)
            }

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                TextButton(onClick = { onOpenLink(PRIVACY_URL) }, shapes = ButtonDefaults.shapes()) {
                    Text(
                        text = stringResource(R.string.pro_privacy_policy),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { onOpenLink(TERMS_URL) }, shapes = ButtonDefaults.shapes()) {
                    Text(
                        text = stringResource(R.string.pro_terms),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private class ProFeature(
    val icon: ImageVector,
    val shape: RoundedPolygon,
    val title: String,
    val body: String,
)

/** A feature's glyph on its own [MaterialShapes] shape. */
@Composable
private fun ShapeIcon(shape: RoundedPolygon, icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(shape.toShape())
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * The main call to action. While billing runs the label gives way to a
 * [LoadingIndicator]; the button keeps its size, so nothing jumps.
 */
@Composable
private fun BuyButton(price: String, inFlight: Boolean, onClick: () -> Unit) {
    val working = stringResource(R.string.state_working)
    val motion = MaterialTheme.motionScheme
    Button(
        // The ViewModel guards re-entry too; this keeps the button enabled so
        // the indicator is not painted in disabled colors.
        onClick = { if (!inFlight) onClick() },
        shapes = ButtonDefaults.shapes(),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ButtonDefaults.LargeContainerHeight)
            .then(
                if (inFlight) {
                    Modifier.politeLiveRegion().semantics { stateDescription = working }
                } else {
                    Modifier
                },
            ),
        contentPadding = ButtonDefaults.LargeContentPadding,
    ) {
        AnimatedContent(
            targetState = inFlight,
            transitionSpec = {
                fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec())
            },
            label = "buyButtonContent",
        ) { busy ->
            if (busy) {
                LoadingIndicator(
                    modifier = Modifier.size(32.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Filled.LockOpen, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = stringResource(R.string.pro_buy_cta, price),
                        style = MaterialTheme.typography.titleMediumEmphasized,
                    )
                }
            }
        }
    }
}

@Composable
private fun RestoreButton(inFlight: Boolean, onClick: () -> Unit) {
    val working = stringResource(R.string.state_working)
    val motion = MaterialTheme.motionScheme
    TextButton(
        onClick = { if (!inFlight) onClick() },
        shapes = ButtonDefaults.shapes(),
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (inFlight) {
                    Modifier.politeLiveRegion().semantics { stateDescription = working }
                } else {
                    Modifier
                },
            ),
    ) {
        AnimatedContent(
            targetState = inFlight,
            transitionSpec = {
                fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec())
            },
            label = "restoreButtonContent",
        ) { busy ->
            if (busy) {
                LoadingIndicator(modifier = Modifier.size(24.dp))
            } else {
                Text(stringResource(R.string.pro_restore))
            }
        }
    }
}

/** Inline status card for states the purchase button cannot express. */
@Composable
private fun InlineNotice(
    text: String,
    container: Color,
    content: Color,
    busy: Boolean = false,
) {
    val animatedContainer by animateColorAsState(
        targetValue = container,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "noticeContainer",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(animatedContainer)
            .padding(16.dp)
            .politeLiveRegion(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (busy) {
            LoadingIndicator(modifier = Modifier.size(32.dp), color = content)
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = content,
            modifier = Modifier.weight(1f),
        )
    }
}

private data class ProMessages(
    val purchaseCompleted: String,
    val purchasePending: String,
    val purchaseCancelled: String,
    val purchaseFailed: String,
    val billingUnavailable: String,
    val restored: String,
    val nothingToRestore: String,
    val restoreFailed: String,
)

// --- Previews ---------------------------------------------------------------

private val PreviewProduct = ProProduct(
    productId = "pro",
    title = "FastMask Pro",
    description = "",
    formattedPrice = "14,99 zł",
)

@Composable
private fun ProPreviewHost(state: ProUiState, celebrate: Boolean = false) {
    FastMaskTheme {
        ProContent(
            state = state,
            snackbarHostState = remember { SnackbarHostState() },
            celebrate = celebrate,
            onClose = {},
            onBuy = {},
            onRetry = {},
            onRestore = {},
            onOpenLink = {},
        )
    }
}

@Preview(name = "Ready", showBackground = true, heightDp = 1100)
@Composable
private fun ProReadyPreview() = ProPreviewHost(
    ProUiState(product = PreviewProduct, productState = ProductState.READY),
)

@Preview(
    name = "Ready dark",
    showBackground = true,
    heightDp = 1100,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ProReadyDarkPreview() = ProPreviewHost(
    ProUiState(product = PreviewProduct, productState = ProductState.READY),
)

@Preview(name = "Purchasing", showBackground = true, heightDp = 1100)
@Composable
private fun ProPurchasingPreview() = ProPreviewHost(
    ProUiState(product = PreviewProduct, productState = ProductState.READY, purchaseInFlight = true),
)

@Preview(name = "Loading", showBackground = true, heightDp = 1100)
@Composable
private fun ProLoadingPreview() = ProPreviewHost(ProUiState())

@Preview(name = "Unavailable", showBackground = true, heightDp = 1100)
@Composable
private fun ProUnavailablePreview() = ProPreviewHost(
    ProUiState(productState = ProductState.UNAVAILABLE),
)

@Preview(name = "Pending", showBackground = true, heightDp = 1100)
@Composable
private fun ProPendingPreview() = ProPreviewHost(
    ProUiState(status = ProStatus.PENDING, product = PreviewProduct, productState = ProductState.READY),
)

@Preview(name = "Owned", showBackground = true, heightDp = 1100)
@Composable
private fun ProOwnedPreview() = ProPreviewHost(
    ProUiState(status = ProStatus.PRO, product = PreviewProduct, productState = ProductState.READY),
    celebrate = true,
)

@Preview(
    name = "Owned dark",
    showBackground = true,
    heightDp = 1100,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ProOwnedDarkPreview() = ProPreviewHost(
    ProUiState(status = ProStatus.PRO, product = PreviewProduct, productState = ProductState.READY),
    celebrate = true,
)
