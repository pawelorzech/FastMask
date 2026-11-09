package com.fastmask.ui.components

import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun DialogTextButton(
    text: String,
    onClick: () -> Unit,
    color: Color = Color.Unspecified,
) {
    TextButton(
        onClick = onClick,
        shapes = ButtonDefaults.shapes(),
        colors = if (color == Color.Unspecified) {
            ButtonDefaults.textButtonColors()
        } else {
            ButtonDefaults.textButtonColors(contentColor = color)
        },
    ) {
        Text(text)
    }
}
