package com.fastmask.ui.pro

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fastmask.R
import com.fastmask.domain.model.ProStatus
import com.fastmask.ui.components.HairlineDivider
import com.fastmask.ui.components.MonoLabel
import com.fastmask.ui.components.PillButton
import com.fastmask.ui.components.PillButtonVariant
import com.fastmask.ui.components.PillIconButton
import com.fastmask.ui.theme.FastMaskExtras
import com.fastmask.ui.theme.MonoSmallStyle

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
    val extras = FastMaskExtras.current
    val snackbarHostState = remember { SnackbarHostState() }

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

    val backDesc = stringResource(R.string.navigate_back)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PillIconButton(
                    // PAYWALL_CLOSED is tracked in the ViewModel's onCleared so
                    // gesture/system back counts too, not just this button.
                    onClick = onNavigateBack,
                    contentDescription = backDesc,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(top = 8.dp, bottom = 32.dp),
            ) {
                MonoLabel(text = stringResource(R.string.pro_label))
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.pro_title),
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.pro_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = extras.inkSoft,
                )
                Spacer(Modifier.height(28.dp))

                ProFeatureRow(
                    icon = Icons.Filled.Palette,
                    title = stringResource(R.string.pro_feature_accents_title),
                    body = stringResource(R.string.pro_feature_accents_body),
                )
                ProFeatureRow(
                    icon = Icons.Filled.Fingerprint,
                    title = stringResource(R.string.pro_feature_lock_title),
                    body = stringResource(R.string.pro_feature_lock_body),
                )
                ProFeatureRow(
                    icon = Icons.Filled.FileDownload,
                    title = stringResource(R.string.pro_feature_export_title),
                    body = stringResource(R.string.pro_feature_export_body),
                )

                Spacer(Modifier.height(28.dp))

                when {
                    uiState.status == ProStatus.PRO -> {
                        OwnedCard()
                    }

                    uiState.status == ProStatus.PENDING -> {
                        Text(
                            text = stringResource(R.string.pro_purchase_pending),
                            style = MaterialTheme.typography.bodyMedium,
                            color = extras.inkSoft,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                    }

                    uiState.productState == ProductState.LOADING -> {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                color = extras.accent,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }

                    uiState.productState == ProductState.UNAVAILABLE -> {
                        Text(
                            text = stringResource(R.string.pro_billing_unavailable),
                            style = MaterialTheme.typography.bodyMedium,
                            color = extras.inkSoft,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(12.dp))
                        PillButton(
                            text = stringResource(R.string.pro_retry),
                            onClick = viewModel::loadProduct,
                            variant = PillButtonVariant.Ghost,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    else -> {
                        val product = uiState.product
                        PillButton(
                            text = stringResource(
                                R.string.pro_buy_cta,
                                product?.formattedPrice.orEmpty(),
                            ),
                            onClick = { activity?.let(viewModel::buy) },
                            loading = uiState.purchaseInFlight,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.pro_one_time_note),
                            style = MonoSmallStyle,
                            color = extras.inkMuted,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                if (uiState.status != ProStatus.PRO) {
                    Spacer(Modifier.height(16.dp))
                    PillButton(
                        text = stringResource(R.string.pro_restore),
                        onClick = viewModel::restore,
                        loading = uiState.restoreInFlight,
                        variant = PillButtonVariant.Ghost,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(Modifier.height(28.dp))
                HairlineDivider()
                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    LinkText(text = stringResource(R.string.pro_privacy_policy)) {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_URL))
                        if (intent.resolveActivity(context.packageManager) != null) {
                            context.startActivity(intent)
                        }
                    }
                    Spacer(Modifier.width(24.dp))
                    LinkText(text = stringResource(R.string.pro_terms)) {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(TERMS_URL))
                        if (intent.resolveActivity(context.packageManager) != null) {
                            context.startActivity(intent)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProFeatureRow(icon: ImageVector, title: String, body: String) {
    val extras = FastMaskExtras.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = extras.accent,
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = FastMaskExtras.current.inkMuted,
            )
        }
    }
}

@Composable
private fun OwnedCard() {
    val extras = FastMaskExtras.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(extras.status.enabled.container)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = extras.status.enabled.content,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = stringResource(R.string.pro_owned),
            style = MaterialTheme.typography.bodyMedium,
            color = extras.status.enabled.content,
        )
    }
}

@Composable
private fun LinkText(text: String, onClick: () -> Unit) {
    // Play-required legal links on a payment screen: give them a real 48 dp
    // touch target and a button role for TalkBack; visual size is unchanged
    // (the text centers inside the enlarged hit area).
    Text(
        text = text,
        style = MonoSmallStyle,
        color = FastMaskExtras.current.inkMuted,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick, role = Role.Button)
            .heightIn(min = 48.dp)
            .wrapContentHeight(Alignment.CenterVertically)
            .padding(horizontal = 4.dp),
    )
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
