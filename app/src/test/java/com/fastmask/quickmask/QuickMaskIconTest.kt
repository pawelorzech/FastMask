package com.fastmask.quickmask

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The two entry-point icons, and why they cannot be one file.
 *
 * `ic_quick_mask` was an envelope filled with hard white. Two failures came out
 * of that single resource:
 *
 *  - In the launcher's long-press menu the "New mask" row showed an empty white
 *    circle. Launcher3 wraps a non-adaptive shortcut icon in a white disc and
 *    tints nothing, so white ink landed on white paper.
 *  - In the Quick Settings tile picker the app was represented by an envelope,
 *    which reads as "something about mail" and shares nothing with the domino
 *    mask on the home screen.
 *
 * A tinting surface wants a bare silhouette; a non-tinting one wants an icon
 * that brings its own background. Hence two resources.
 */
class QuickMaskIconTest {

    private fun res(path: String) = File("src/main/res/$path")

    private val glyph = res("drawable/ic_quick_mask.xml").readText()
    private val shortcutIcon = res("drawable/ic_shortcut_quick_mask.xml").readText()
    private val shortcutForeground = res("drawable/ic_shortcut_quick_mask_foreground.xml").readText()
    private val shortcuts = res("xml/shortcuts.xml").readText()
    private val manifest = File("src/main/AndroidManifest.xml").readText()

    // --- the mark ------------------------------------------------------------

    @Test
    fun `the glyph is a mask, not an envelope`() {
        // The envelope path was one closed rectangle plus a flap. The mask is an
        // outer silhouette with two eye holes punched out of it — three
        // subpaths, and the holes only survive with an even-odd fill.
        assertEquals(
            "ic_quick_mask must be the domino mask: an outer silhouette and two eye holes",
            3,
            Regex("""[Mm]-?[\d.]""").findAll(pathData(glyph)).count(),
        )
        assertTrue(
            "the eye holes need evenOdd, otherwise they fill in solid",
            glyph.contains("""android:fillType="evenOdd""""),
        )
    }

    @Test
    fun `both icons draw the same silhouette`() {
        // The tile glyph is the shortcut's foreground scaled from 108 to 24, so
        // the two must agree on the number of contours. If someone redraws one
        // of them, this is the tripwire.
        assertEquals(
            "the shortcut foreground and the tile glyph have drifted apart",
            Regex("""[Mm]-?[\d.]""").findAll(pathData(glyph)).count(),
            Regex("""[Mm]-?[\d.]""").findAll(pathData(shortcutForeground)).count(),
        )
    }

    // --- the tinting contract: Quick Settings + notifications ----------------

    @Test
    fun `the tile keeps the monochrome glyph`() {
        // SystemUI tints the tile icon itself, so a bare white silhouette is
        // exactly right there — and it is the same drawable the notification
        // small icon uses, which the platform flattens to a mask anyway.
        assertTrue(
            "QuickMaskTileService must keep pointing at the monochrome glyph",
            manifest.contains("""android:icon="@drawable/ic_quick_mask""""),
        )
        assertTrue(
            "the tinted glyph is white by design",
            glyph.contains("""android:fillColor="#FFFFFFFF""""),
        )
    }

    // --- the non-tinting contract: the launcher shortcut ---------------------

    @Test
    fun `the shortcut no longer uses the untinted white glyph`() {
        assertFalse(
            "the shortcut points back at ic_quick_mask — white ink on the white disc " +
                "Launcher3 wraps legacy icons in is the empty circle users reported",
            shortcuts.contains("""android:icon="@drawable/ic_quick_mask""""),
        )
        assertTrue(
            "the shortcut must use its own icon resource",
            shortcuts.contains("""android:icon="@drawable/ic_shortcut_quick_mask""""),
        )
    }

    @Test
    fun `the shortcut icon carries its own background`() {
        // An adaptive icon is the fix that does not depend on guessing the
        // launcher popup's colour: opaque background, glyph on top, legible in
        // light and dark alike.
        assertTrue(
            "the shortcut icon must be an adaptive icon",
            shortcutIcon.contains("<adaptive-icon"),
        )
        assertTrue(
            "an adaptive icon without a background layer is transparent again",
            shortcutIcon.contains("<background android:drawable="),
        )
        assertTrue(
            "the shortcut icon must draw the mask foreground",
            shortcutIcon.contains("@drawable/ic_shortcut_quick_mask_foreground"),
        )
    }

    @Test
    fun `the shortcut glyph is not white`() {
        // The whole point: on the cream background a white mask would be the
        // same invisible icon in a new file.
        assertFalse(
            "the shortcut foreground is hard white — that is the bug being fixed",
            Regex("""fillColor="#(FF)?FFFFFF"""", RegexOption.IGNORE_CASE)
                .containsMatchIn(shortcutForeground),
        )
        assertTrue(
            "the shortcut glyph uses the app's amber (AccentAmber, #A8530F)",
            shortcutForeground.contains("#A8530F"),
        )
    }

    @Test
    fun `the shortcut glyph stays inside the adaptive safe zone`() {
        // A launcher may mask an adaptive icon down to a 66dp circle inside the
        // 108dp viewport. Anything outside can be cropped away.
        val coords = Regex("""(-?\d+(?:\.\d+)?),(-?\d+(?:\.\d+)?)""")
            .findAll(pathData(shortcutForeground))
            .map { it.groupValues[1].toDouble() to it.groupValues[2].toDouble() }
            .toList()

        assertTrue("no coordinates parsed out of the shortcut foreground", coords.isNotEmpty())
        val worst = coords.maxByOrNull { (x, y) ->
            val dx = x - 54.0
            val dy = y - 54.0
            dx * dx + dy * dy
        }!!
        val radius = kotlin.math.hypot(worst.first - 54.0, worst.second - 54.0)
        assertTrue(
            "the mask reaches $radius dp from the centre of the 108dp viewport; the safe " +
                "circle is 33dp and a launcher may crop the rest (worst point: $worst)",
            radius <= 33.0,
        )
    }

    private fun pathData(vectorXml: String): String =
        Regex("""android:pathData="([^"]*)"""").find(vectorXml)?.groupValues?.get(1)
            ?: error("no pathData in vector")
}
