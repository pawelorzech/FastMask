package com.fastmask.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fastmask.R
import com.fastmask.ui.components.PillButton
import com.fastmask.ui.theme.FastMaskExtras

/**
 * Full-screen gate shown while the biometric app lock is engaged. Content
 * behind it is never composed, so nothing sensitive can flash through.
 */
@Composable
fun LockScreen(onUnlockClick: () -> Unit) {
    val extras = FastMaskExtras.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            tint = extras.accent,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.app_lock_locked_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = extras.inkSoft,
        )
        Spacer(Modifier.height(28.dp))
        PillButton(
            text = stringResource(R.string.app_lock_unlock),
            onClick = onUnlockClick,
        )
    }
}
