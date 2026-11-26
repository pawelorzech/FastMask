package com.fastmask.ui.adaptive

import androidx.compose.material3.adaptive.HingeInfo
import androidx.compose.material3.adaptive.Posture
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.computeWindowSizeClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveInfoTest {

    private fun adaptiveAt(
        widthDp: Int,
        heightDp: Int,
        posture: Posture = Posture(),
    ): FastMaskAdaptiveInfo {
        val wsc = WindowSizeClass.BREAKPOINTS_V2.computeWindowSizeClass(widthDp, heightDp)
        return FastMaskAdaptiveInfo.from(WindowAdaptiveInfo(wsc, posture))
    }

    @Test
    fun `classifies all breakpoints V2 width and height buckets`() {
        val phonePortrait = adaptiveAt(411, 891)
        assertEquals(FastMaskWidthClass.COMPACT, phonePortrait.widthClass)
        assertEquals(FastMaskHeightClass.MEDIUM, phonePortrait.heightClass)
        assertFalse(FastMaskAdaptiveRules.usesWorkspace(phonePortrait))
        assertEquals(1, FastMaskAdaptiveRules.paneCount(phonePortrait))

        val phoneLandscape = adaptiveAt(891, 411)
        assertEquals(FastMaskWidthClass.EXPANDED, phoneLandscape.widthClass)
        assertEquals(FastMaskHeightClass.COMPACT, phoneLandscape.heightClass)
        assertFalse("landscape phone must stay on the phone NavHost", FastMaskAdaptiveRules.usesWorkspace(phoneLandscape))

        val shortLandscape = adaptiveAt(580, 340)
        assertEquals(FastMaskWidthClass.COMPACT, shortLandscape.widthClass)
        assertEquals(FastMaskHeightClass.COMPACT, shortLandscape.heightClass)
        assertFalse(FastMaskAdaptiveRules.usesWorkspace(shortLandscape))

        val medium = adaptiveAt(673, 841)
        assertEquals(FastMaskWidthClass.MEDIUM, medium.widthClass)
        assertEquals(FastMaskHeightClass.MEDIUM, medium.heightClass)
        assertTrue(FastMaskAdaptiveRules.usesWorkspace(medium))
        assertEquals(1, FastMaskAdaptiveRules.paneCount(medium))

        val fold1x1 = adaptiveAt(864, 864)
        assertEquals(FastMaskWidthClass.EXPANDED, fold1x1.widthClass)
        assertTrue(FastMaskAdaptiveRules.usesWorkspace(fold1x1))
        assertEquals(2, FastMaskAdaptiveRules.paneCount(fold1x1))

        val expanded = adaptiveAt(900, 841)
        assertEquals(FastMaskWidthClass.EXPANDED, expanded.widthClass)
        assertTrue(FastMaskAdaptiveRules.usesWorkspace(expanded))
        assertEquals(2, FastMaskAdaptiveRules.paneCount(expanded))

        val fold4x3 = adaptiveAt(1024, 768)
        assertEquals(FastMaskWidthClass.EXPANDED, fold4x3.widthClass)
        assertTrue(FastMaskAdaptiveRules.usesWorkspace(fold4x3))
        assertEquals(2, FastMaskAdaptiveRules.paneCount(fold4x3))

        val large = adaptiveAt(1280, 800)
        assertEquals(FastMaskWidthClass.LARGE, large.widthClass)
        assertTrue(FastMaskAdaptiveRules.usesWorkspace(large))
        assertEquals(2, FastMaskAdaptiveRules.paneCount(large))

        val extraLarge = adaptiveAt(1680, 960)
        assertEquals(FastMaskWidthClass.EXTRA_LARGE, extraLarge.widthClass)
        assertEquals(FastMaskHeightClass.EXPANDED, extraLarge.heightClass)
        assertTrue(FastMaskAdaptiveRules.usesWorkspace(extraLarge))
        assertEquals(2, FastMaskAdaptiveRules.paneCount(extraLarge))
    }

    @Test
    fun `derives tabletop and book postures and adjusts pane directive`() {
        val horizontalHinge = HingeInfo(
            bounds = Rect(0f, 390f, 840f, 410f),
            isFlat = false,
            isVertical = false,
            isSeparating = true,
            isOccluding = false,
        )
        val tabletop = adaptiveAt(
            widthDp = 840,
            heightDp = 800,
            posture = Posture(isTabletop = true, hingeList = listOf(horizontalHinge)),
        )
        assertTrue(tabletop.posture.isTabletop)
        assertFalse(tabletop.posture.isBookPosture)
        assertTrue(tabletop.posture.hasSeparatingHorizontalHinge)
        assertTrue(tabletop.posture.hasSeparatingHinge)

        val verticalHinge = HingeInfo(
            bounds = Rect(350f, 0f, 370f, 840f),
            isFlat = false,
            isVertical = true,
            isSeparating = true,
            isOccluding = true,
        )
        val mediumBook = adaptiveAt(
            widthDp = 720,
            heightDp = 840,
            posture = Posture(isTabletop = false, hingeList = listOf(verticalHinge)),
        )
        assertFalse(mediumBook.posture.isTabletop)
        assertTrue(mediumBook.posture.isBookPosture)
        assertTrue(mediumBook.posture.hasSeparatingVerticalHinge)
        assertEquals(2, FastMaskAdaptiveRules.paneCount(mediumBook))
        assertEquals(2, FastMaskAdaptiveRules.paneDirective(mediumBook).maxHorizontalPartitions)
    }

    @Test
    fun `panes never stack vertically on tall tablets`() {
        val portraitTablet = adaptiveAt(800, 1280)
        val directive = FastMaskAdaptiveRules.paneDirective(portraitTablet)
        assertEquals(1, directive.maxVerticalPartitions)
        assertEquals(0.dp, directive.verticalPartitionSpacerSize)

        val largeDirective = FastMaskAdaptiveRules.paneDirective(adaptiveAt(1280, 1000))
        assertEquals(1, largeDirective.maxVerticalPartitions)
        assertEquals(0.dp, largeDirective.verticalPartitionSpacerSize)
    }
}
