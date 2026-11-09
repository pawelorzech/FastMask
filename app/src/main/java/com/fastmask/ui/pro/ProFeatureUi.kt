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

/** Everything the UI draws for one [ProFeature]: its icon, badge shape and paywall copy. */
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

/** Analytics source for a paywall opened from the Pro row in Settings (a locked feature uses [ProFeature.source]). */
internal const val PRO_SOURCE_SETTINGS = "settings"

/** Analytics source when the paywall route carries none. */
internal const val PRO_SOURCE_UNKNOWN = "unknown"
