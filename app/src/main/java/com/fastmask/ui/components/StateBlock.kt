package com.fastmask.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fastmask.ui.accessibility.politeLiveRegion

@Composable
internal fun StateBlock(
    icon: ImageVector,
    title: String,
    body: String,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    liveRegion: Boolean = false,
    illustrationShape: Shape = MaterialShapes.Cookie9Sided.toShape(),
    illustrationContainer: Color = MaterialTheme.colorScheme.secondaryContainer,
    illustrationContent: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
            .then(if (liveRegion) Modifier.politeLiveRegion() else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        StateIllustration(
            icon = icon,
            shape = illustrationShape,
            container = illustrationContainer,
            content = illustrationContent,
        )
        Spacer(Modifier.size(24.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmallEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.size(24.dp))
            action()
        }
    }
}
