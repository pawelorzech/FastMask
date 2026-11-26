package com.fastmask.ui.adaptive

import android.content.ClipData
import android.view.View
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropTransferData

private const val MASK_CLIP_LABEL = "fastmask-address"

/**
 * Allows dragging a masked email address out of FastMask into another window or split-screen app
 * as plain text (`DRAG_FLAG_GLOBAL`).
 */
fun Modifier.maskAddressDragSource(address: String): Modifier = dragAndDropSource(
    transferData = {
        DragAndDropTransferData(
            clipData = ClipData.newPlainText(MASK_CLIP_LABEL, address),
            flags = View.DRAG_FLAG_GLOBAL,
        )
    },
)
