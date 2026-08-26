package com.fastmask.quickmask

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The feature glyph and brand mark, and why they cannot be one file.
 *
 * `ic_quick_mask` was an envelope filled with hard white. Two failures came out
 * of that single resource:
 *
 *  - In the launcher's long-press menu the "New mask" row showed an empty white
 *    circle. Launcher3 wraps a non-adaptive shortcut icon in a white disc and
 *    tints nothing, so white ink landed on white paper.
 *  - A Quick Settings tile needs a literal action glyph, while the launcher
 *    shortcut needs the broader FastMask brand mark.
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
    fun `the shortcut uses the three-part privacy fold brand mark`() {
        // Quick Mask remains a literal action glyph. The launcher shortcut uses
        // the brand's two paper folds plus the amber turnover accent.
        assertEquals(
            "the Privacy Fold must keep two paper planes and one turnover accent",
            3,
            Regex("<path").findAll(shortcutForeground).count(),
        )
        assertFalse(
            "the brand mark regressed to the old punched-eye domino mask",
            shortcutForeground.contains("evenOdd"),
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
            "launcher navy would hide the navy brand mark on the shortcut",
            shortcutIcon.contains("@drawable/ic_shortcut_background"),
        )
        assertTrue(
            "the shortcut icon must draw the brand foreground",
            shortcutIcon.contains("@drawable/ic_shortcut_quick_mask_foreground"),
        )
    }

    @Test
    fun `the shortcut glyph is not white`() {
        // The whole point: on the cream background a white mask would be the
        // same invisible icon in a new file.
        assertFalse(
            "the shortcut foreground is hard white — it would disappear on its light background",
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
            "the mark reaches $radius dp from the centre of the 108dp viewport; the safe " +
                "circle is 33dp and a launcher may crop the rest (worst point: $worst)",
            radius <= 33.0,
        )
    }

    @Test
    fun `the Play icon is an opaque 512 square`() {
        val icon = pngInfo(File("../marketing/play/icon-512.png"))

        assertEquals(512, icon.width)
        assertEquals(512, icon.height)
        assertFalse("Google Play icon must not carry alpha", icon.hasAlpha)
    }

    @Test
    fun `every adaptive density has matching color and monochrome layers`() {
        val expectedSizes = mapOf(
            "mdpi" to 108,
            "hdpi" to 162,
            "xhdpi" to 216,
            "xxhdpi" to 324,
            "xxxhdpi" to 432,
        )

        expectedSizes.forEach { (density, size) ->
            val color = pngInfo(res("drawable-$density/ic_launcher_foreground.png"))
            val mono = pngInfo(res("drawable-$density/ic_launcher_monochrome.png"))
            assertEquals("wrong $density foreground width", size, color.width)
            assertEquals("wrong $density foreground height", size, color.height)
            assertEquals("wrong $density monochrome width", size, mono.width)
            assertEquals("wrong $density monochrome height", size, mono.height)
            assertTrue("$density foreground lost transparency", color.hasAlpha)
            assertTrue("$density monochrome lost transparency", mono.hasAlpha)
        }
    }

    /** Reads only PNG signature + IHDR, keeping this JVM test independent of java.desktop. */
    private fun pngInfo(file: File): PngInfo {
        val bytes = file.readBytes()
        assertTrue("${file.path} is too short to be a PNG", bytes.size >= 26)
        val signature = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
        assertTrue("${file.path} has no PNG signature", bytes.copyOfRange(0, 8).contentEquals(signature))
        assertEquals("${file.path} has no IHDR first", "IHDR", bytes.copyOfRange(12, 16).toString(Charsets.US_ASCII))

        fun intAt(offset: Int): Int =
            ((bytes[offset].toInt() and 0xff) shl 24) or
                ((bytes[offset + 1].toInt() and 0xff) shl 16) or
                ((bytes[offset + 2].toInt() and 0xff) shl 8) or
                (bytes[offset + 3].toInt() and 0xff)

        // PNG colour types 4 and 6 carry an alpha channel.
        return PngInfo(intAt(16), intAt(20), bytes[25].toInt() in setOf(4, 6))
    }

    private data class PngInfo(val width: Int, val height: Int, val hasAlpha: Boolean)

    private fun pathData(vectorXml: String): String =
        Regex("""android:pathData="([^"]*)"""").find(vectorXml)?.groupValues?.get(1)
            ?: error("no pathData in vector")
}
