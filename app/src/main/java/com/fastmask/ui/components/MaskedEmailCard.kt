package com.fastmask.ui.components

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fastmask.domain.model.EmailState
import com.fastmask.domain.model.MaskedEmail
import com.fastmask.ui.theme.FastMaskStatusColors

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MaskedEmailCard(
    maskedEmail: MaskedEmail,
    onClick: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    modifier: Modifier = Modifier,
    isScrolling: Boolean = false
) {
    val haptic = LocalHapticFeedback.current

    val stateDescription = when (maskedEmail.state) {
        EmailState.ENABLED -> "Enabled"
        EmailState.DISABLED -> "Disabled"
        EmailState.DELETED -> "Deleted"
        EmailState.PENDING -> "Pending"
    }

    with(sharedTransitionScope) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .then(
                    if (!isScrolling) {
                        Modifier.sharedBounds(
                            sharedContentState = rememberSharedContentState(key = "card-${maskedEmail.id}"),
                            animatedVisibilityScope = animatedContentScope
                        )
                    } else Modifier
                )
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
                .semantics {
                    contentDescription = "${maskedEmail.displayName}, ${maskedEmail.email}, $stateDescription"
                },
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusIcon(
                    state = maskedEmail.state,
                    modifier = if (!isScrolling) {
                        Modifier.sharedElement(
                            state = rememberSharedContentState(key = "icon-${maskedEmail.id}"),
                            animatedVisibilityScope = animatedContentScope
                        )
                    } else Modifier
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = maskedEmail.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (!isScrolling) {
                            Modifier.sharedElement(
                                state = rememberSharedContentState(key = "title-${maskedEmail.id}"),
                                animatedVisibilityScope = animatedContentScope
                            )
                        } else Modifier
                    )
                    Text(
                        text = maskedEmail.email,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (!isScrolling) {
                            Modifier.sharedElement(
                                state = rememberSharedContentState(key = "email-${maskedEmail.id}"),
                                animatedVisibilityScope = animatedContentScope
                            )
                        } else Modifier
                    )
                    maskedEmail.forDomain?.let { domain ->
                        Text(
                            text = domain,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusIcon(
    state: EmailState,
    modifier: Modifier = Modifier
) {
    val statusColors = FastMaskStatusColors.current

    val (icon, colorPair) = when (state) {
        EmailState.ENABLED -> Icons.Default.Check to statusColors.enabled
        EmailState.DISABLED -> Icons.Default.Close to statusColors.disabled
        EmailState.DELETED -> Icons.Default.Delete to statusColors.deleted
        EmailState.PENDING -> Icons.Default.HourglassEmpty to statusColors.pending
    }

    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(colorPair.container),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = state.name,
            modifier = Modifier.size(24.dp),
            tint = colorPair.content
        )
    }
}
