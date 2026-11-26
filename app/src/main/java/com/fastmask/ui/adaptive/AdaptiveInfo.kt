package com.fastmask.ui.adaptive

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.allHorizontalHingeBounds
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.HingePolicy
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth
import androidx.compose.material3.adaptive.separatingHorizontalHingeBounds
import androidx.compose.material3.adaptive.separatingVerticalHingeBounds
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass

enum class FastMaskWidthClass { COMPACT, MEDIUM, EXPANDED, LARGE, EXTRA_LARGE }

enum class FastMaskHeightClass { COMPACT, MEDIUM, EXPANDED }

@Immutable
data class FastMaskPosture(
    val isTabletop: Boolean = false,
    val isBookPosture: Boolean = false,
    val separatingVerticalHinges: List<Rect> = emptyList(),
    val separatingHorizontalHinges: List<Rect> = emptyList(),
    val allHorizontalHinges: List<Rect> = emptyList(),
) {
    val hasSeparatingVerticalHinge: Boolean get() = separatingVerticalHinges.isNotEmpty()
    val hasSeparatingHorizontalHinge: Boolean get() = separatingHorizontalHinges.isNotEmpty()
    val hasSeparatingHinge: Boolean get() = hasSeparatingVerticalHinge || hasSeparatingHorizontalHinge
}

@Immutable
data class FastMaskAdaptiveInfo(
    val raw: WindowAdaptiveInfo,
    val widthClass: FastMaskWidthClass,
    val heightClass: FastMaskHeightClass,
    val posture: FastMaskPosture,
) {
    companion object {
        fun from(info: WindowAdaptiveInfo): FastMaskAdaptiveInfo {
            val wsc = info.windowSizeClass
            val widthClass = when {
                wsc.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXTRA_LARGE_LOWER_BOUND) ->
                    FastMaskWidthClass.EXTRA_LARGE
                wsc.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_LARGE_LOWER_BOUND) ->
                    FastMaskWidthClass.LARGE
                wsc.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) ->
                    FastMaskWidthClass.EXPANDED
                wsc.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) ->
                    FastMaskWidthClass.MEDIUM
                else -> FastMaskWidthClass.COMPACT
            }
            val heightClass = when {
                wsc.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_EXPANDED_LOWER_BOUND) ->
                    FastMaskHeightClass.EXPANDED
                wsc.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND) ->
                    FastMaskHeightClass.MEDIUM
                else -> FastMaskHeightClass.COMPACT
            }
            val wp = info.windowPosture
            val isBook = wp.hingeList.any { it.isVertical && !it.isFlat }
            val posture = FastMaskPosture(
                isTabletop = wp.isTabletop,
                isBookPosture = isBook,
                separatingVerticalHinges = wp.separatingVerticalHingeBounds,
                separatingHorizontalHinges = wp.separatingHorizontalHingeBounds,
                allHorizontalHinges = wp.allHorizontalHingeBounds,
            )
            return FastMaskAdaptiveInfo(info, widthClass, heightClass, posture)
        }
    }
}

object FastMaskAdaptiveRules {
    /**
     * The list-detail workspace is reserved for tablet- and unfolded-sized windows.
     * Phones in portrait or landscape (wide but compact height) keep the single-screen
     * NavHost flow untouched.
     */
    fun usesWorkspace(info: FastMaskAdaptiveInfo): Boolean =
        info.widthClass != FastMaskWidthClass.COMPACT && info.heightClass != FastMaskHeightClass.COMPACT

    fun paneCount(info: FastMaskAdaptiveInfo): Int = when (info.widthClass) {
        FastMaskWidthClass.EXTRA_LARGE,
        FastMaskWidthClass.LARGE,
        FastMaskWidthClass.EXPANDED,
        -> 2
        FastMaskWidthClass.MEDIUM -> if (info.posture.hasSeparatingVerticalHinge) 2 else 1
        FastMaskWidthClass.COMPACT -> 1
    }

    /**
     * Panes only ever sit side by side: forcing [maxVerticalPartitions] = 1 avoids stacking
     * the detail pane below the list on tall portrait tablets.
     */
    @OptIn(ExperimentalMaterial3AdaptiveApi::class)
    fun paneDirective(
        info: FastMaskAdaptiveInfo,
        allowTwoPanesOnMedium: Boolean = false,
    ): PaneScaffoldDirective {
        val base = if (
            (allowTwoPanesOnMedium || info.posture.hasSeparatingVerticalHinge) &&
            info.widthClass == FastMaskWidthClass.MEDIUM
        ) {
            calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth(info.raw, HingePolicy.AvoidSeparating)
        } else {
            calculatePaneScaffoldDirective(info.raw, HingePolicy.AvoidSeparating)
        }
        return base.copy(maxVerticalPartitions = 1, verticalPartitionSpacerSize = 0.dp)
    }
}

@Composable
fun rememberFastMaskAdaptiveInfo(): FastMaskAdaptiveInfo =
    FastMaskAdaptiveInfo.from(currentWindowAdaptiveInfoV2())

val LocalFastMaskAdaptiveInfo = compositionLocalOf<FastMaskAdaptiveInfo?> { null }

@Composable
fun currentFastMaskAdaptiveInfo(): FastMaskAdaptiveInfo =
    LocalFastMaskAdaptiveInfo.current ?: rememberFastMaskAdaptiveInfo()

@Composable
fun ProvideFastMaskAdaptiveInfo(
    info: FastMaskAdaptiveInfo = rememberFastMaskAdaptiveInfo(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalFastMaskAdaptiveInfo provides info, content = content)
}
