package com.fastmask.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.fastmask.ui.theme.FastMaskExtras
import com.fastmask.ui.theme.JetBrainsMono

@Composable
fun DesignInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    hint: String? = null,
    placeholder: String? = null,
    enabled: Boolean = true,
    isError: Boolean = false,
    singleLine: Boolean = true,
    mono: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailing: (@Composable () -> Unit)? = null,
) {
    val extras = FastMaskExtras.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val borderColor = when {
        isError -> extras.status.deleted.content
        focused -> extras.accent
        else -> MaterialTheme.colorScheme.outline
    }
    val shape = RoundedCornerShape(12.dp)
    val textStyle: TextStyle = LocalTextStyle.current.copy(
        color = MaterialTheme.colorScheme.onSurface,
        fontFamily = if (mono) JetBrainsMono else MaterialTheme.typography.bodyLarge.fontFamily,
        fontSize = if (mono) MaterialTheme.typography.bodyMedium.fontSize else MaterialTheme.typography.bodyLarge.fontSize,
    )

    Column(modifier = modifier) {
        if (label != null) {
            MonoLabel(text = label)
            Spacer(Modifier.height(8.dp))
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(extras.inputBg, shape)
                .border(BorderStroke(1.dp, borderColor), shape)
                .padding(PaddingValues(horizontal = 14.dp, vertical = 14.dp)),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) {
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        enabled = enabled,
                        singleLine = singleLine,
                        textStyle = textStyle,
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(extras.accent),
                        visualTransformation = visualTransformation,
                        keyboardOptions = keyboardOptions,
                        keyboardActions = keyboardActions,
                        interactionSource = interaction,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (value.isEmpty() && placeholder != null) {
                        Text(
                            text = placeholder,
                            style = textStyle.copy(color = extras.inkMuted),
                        )
                    }
                }
                if (trailing != null) {
                    Spacer(Modifier.padding(start = 8.dp))
                    trailing()
                }
            }
        }
        val supportText = when {
            isError && hint != null -> hint
            !isError && hint != null -> hint
            else -> null
        }
        if (supportText != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = supportText,
                style = MaterialTheme.typography.labelMedium,
                color = if (isError) extras.status.deleted.content else extras.inkMuted,
            )
        }
    }
}

@Composable
fun ScreenScaffoldPadding(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) { content() }
    }
}
