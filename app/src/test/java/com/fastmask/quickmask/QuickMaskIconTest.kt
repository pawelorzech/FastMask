package com.fastmask.quickmask

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Launcher-shortcut and store-icon requirements, read from the resources as data.
 *
 * `ic_quick_mask` is a bare white silhouette for tinting surfaces (Quick Settings,
 * notifications). Launcher3 wraps a non-adaptive shortcut icon in a white disc and
 * tints nothing, so the shortcut needs its own icon: white ink on that disc was the
 * empty circle users reported.
 */
class QuickMaskIconTest {

    private fun res(path: String) = File("src/main/res/$path")

    private val shortcutForeground = res("drawable/ic_shortcut_quick_mask_foreground.xml").readText()

    // --- the non-tinting contract: the launcher shortcut ---------------------

    @Test
    fun `the shortcut glyph is not white`() {
        // The whole point: on the cream background a white mask would be the
        // same invisible icon in a new file.
        assertFalse(
            "the shortcut foreground is hard white — it would disappear on its light background",
            Regex("""fillColor="#(FF)?FFFFFF"""", RegexOption.IGNORE_CASE)
                .containsMatchIn(shortcutForeground),
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
    fun `the themed monochrome layer is one evenOdd path`() {
        // One evenOdd path so the @ is cut out of the cookie, not painted on it:
        // launchers tint the monochrome layer by alpha only.
        val mono = res("drawable/ic_launcher_monochrome.xml").readText()
        assertEquals(1, Regex("<path").findAll(mono).count())
        assertTrue(mono.contains("android:fillType=\"evenOdd\""))
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
