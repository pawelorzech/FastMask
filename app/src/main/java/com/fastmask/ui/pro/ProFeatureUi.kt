package com.fastmask.ui.pro

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.MaterialShapes
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.graphics.shapes.RoundedPolygon
import com.fastmask.R
import com.fastmask.domain.model.ProFeature

internal class ProFeatureStyle(
    val icon: ImageVector,
    val shape: RoundedPolygon,
    @param:StringRes val titleRes: Int,
    @param:StringRes val bodyRes: Int,
)

internal val ProFeature.style: ProFeatureStyle
    get() = when (this) {
        ProFeature.ACCENT -> ProFeatureStyle(
            Icons.Filled.Palette,
            MaterialShapes.Cookie4Sided,
            R.string.pro_feature_accents_title,
            R.string.pro_feature_accents_body,
        )
        ProFeature.APP_LOCK -> ProFeatureStyle(
            Icons.Filled.Fingerprint,
            MaterialShapes.Clover4Leaf,
            R.string.pro_feature_lock_title,
            R.string.pro_feature_lock_body,
        )
        ProFeature.EXPORT -> ProFeatureStyle(
            Icons.Filled.FileDownload,
            MaterialShapes.Pentagon,
            R.string.pro_feature_export_title,
            R.string.pro_feature_export_body,
        )
    }

internal const val PRO_SOURCE_SETTINGS = "settings"

internal const val PRO_SOURCE_UNKNOWN = "unknown"
