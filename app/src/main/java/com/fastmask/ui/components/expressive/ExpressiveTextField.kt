package com.fastmask.ui.components.expressive

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/**
 * The one text field style of the app: a filled `surfaceContainerHigh` container with 16dp corners
 * all round (tightening to 12dp on focus with the fast spatial spring), no indicator line, a 2dp
 * primary outline while focused and a 2dp error outline when invalid. It matches the list's search
 * pill. Label, placeholder, supporting/error text and trailing content are the stock [TextField]
 * slots, so accessibility behaves exactly like the platform component.
 */
@Composable
fun ExpressiveTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String? = null,
    placeholder: String? = null,
    supportingText: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    singleLine: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val focused by interactionSource.collectIsFocusedAsState()
    val corner by animateDpAsState(
        targetValue = if (focused) 12.dp else 16.dp,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "fieldCorner",
    )
    // Spatial springs overshoot; a negative corner radius is invalid.
    val shape = RoundedCornerShape(corner.coerceAtLeast(0.dp))
    val outline by animateColorAsState(
        targetValue = when {
            isError -> MaterialTheme.colorScheme.error
            focused -> MaterialTheme.colorScheme.primary
            else -> Color.Transparent
        },
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "fieldOutline",
    )
    val container = MaterialTheme.colorScheme.surfaceContainerHigh

    Column(modifier = Modifier.fillMaxWidth()) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier
                .fillMaxWidth()
                .border(width = 2.dp, color = outline, shape = shape),
            enabled = enabled,
            singleLine = singleLine,
            isError = isError,
            label = label?.let { { Text(it) } },
            placeholder = placeholder?.let { { Text(it) } },
            trailingIcon = trailingIcon,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            interactionSource = interactionSource,
            shape = shape,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = container,
                unfocusedContainerColor = container,
                disabledContainerColor = container,
                errorContainerColor = container,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                errorIndicatorColor = Color.Transparent,
            ),
        )
        // Outside the outlined container, so the focus/error outline hugs the field only.
        if (supportingText != null) {
            Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp)) {
                ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                    CompositionLocalProvider(
                        LocalContentColor provides if (isError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    ) { supportingText() }
                }
            }
        }
    }
}
