package com.fastmask.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fastmask.R
import com.fastmask.domain.model.MaskedEmail
import com.fastmask.ui.components.MaskDescriptionField
import com.fastmask.ui.components.MaskDomainField
import com.fastmask.ui.components.MaskUrlField
import com.fastmask.ui.components.expressive.SegmentedGroup
import com.fastmask.ui.components.expressive.SegmentedRow
import com.fastmask.ui.util.RelativeTime

@Composable
internal fun ReadFields(email: MaskedEmail) {
    val dash = "—"
    val rows = listOf(
        stringResource(R.string.email_detail_description_label) to
            (email.description?.takeIf { it.isNotBlank() } ?: dash),
        stringResource(R.string.email_detail_domain_label) to
            (email.forDomain?.takeIf { it.isNotBlank() } ?: dash),
        stringResource(R.string.email_detail_url_label) to
            (email.url?.takeIf { it.isNotBlank() } ?: dash),
        stringResource(R.string.email_detail_created) to
            (email.createdAt?.let { RelativeTime.full(it) } ?: dash),
        stringResource(R.string.email_detail_last_message) to
            (email.lastMessageAt?.let { RelativeTime.full(it) } ?: stringResource(R.string.time_never)),
        stringResource(R.string.email_detail_created_by) to (email.createdBy ?: dash),
    )
    SegmentedGroup(count = rows.size) { index ->
        val (label, value) = rows[index]
        SegmentedRow(
            index = index,
            count = rows.size,
            supporting = {
                SelectionContainer {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            },
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun EditFields(
    uiState: MaskedEmailDetailUiState,
    onDescriptionChange: (String) -> Unit,
    onForDomainChange: (String) -> Unit,
    onUrlChange: (String) -> Unit,
) {
    val domainFocus = remember { FocusRequester() }
    val urlFocus = remember { FocusRequester() }
    val enabled = !uiState.isUpdating
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        MaskDescriptionField(
            value = uiState.editedDescription,
            onValueChange = onDescriptionChange,
            label = stringResource(R.string.email_detail_description_label),
            enabled = enabled,
            onNext = { domainFocus.requestFocus() },
        )
        MaskDomainField(
            value = uiState.editedForDomain,
            onValueChange = onForDomainChange,
            label = stringResource(R.string.email_detail_domain_label),
            enabled = enabled,
            onNext = { urlFocus.requestFocus() },
            modifier = Modifier.focusRequester(domainFocus),
        )
        MaskUrlField(
            value = uiState.editedUrl,
            onValueChange = onUrlChange,
            label = stringResource(R.string.email_detail_url_label),
            enabled = enabled,
            modifier = Modifier.focusRequester(urlFocus),
        )
    }
}
