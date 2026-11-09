package com.fastmask.ui.list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.unit.dp
import com.fastmask.R
import com.fastmask.ui.accessibility.politeLiveRegion
import com.fastmask.ui.components.StateBlock

private val ListStatePadding = PaddingValues(horizontal = 32.dp, vertical = 48.dp)

@Composable
internal fun EmptyBlock(onCreate: () -> Unit) {
    StateBlock(
        icon = Icons.Filled.Inbox,
        title = stringResource(R.string.email_list_empty),
        body = stringResource(R.string.email_list_empty_sub),
        contentPadding = ListStatePadding,
        action = {
            Button(onClick = onCreate) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.email_list_create_fab))
            }
        },
    )
}

@Composable
internal fun NoMatchesBlock(hasQuery: Boolean, onClearSearch: () -> Unit) {
    StateBlock(
        icon = Icons.Filled.SearchOff,
        title = stringResource(R.string.email_list_no_matches),
        body = stringResource(R.string.email_list_no_matches_sub),
        contentPadding = ListStatePadding,
        action = if (hasQuery) {
            {
                FilledTonalButton(onClick = onClearSearch) {
                    Text(stringResource(R.string.email_list_clear_search))
                }
            }
        } else {
            null
        },
    )
}

@Composable
internal fun LoadingShimmer(loadingDescription: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .politeLiveRegion()
            .semantics { contentDescription = loadingDescription },
        contentAlignment = Alignment.Center,
    ) {
        ContainedLoadingIndicator()
    }
}
