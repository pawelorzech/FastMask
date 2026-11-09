package com.fastmask.quickmask

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.hypot

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
        // Android's adaptive-icon guideline: the 66dp safe zone centred in the 108dp viewport is
        // never clipped by an OEM mask, so a 33dp radius from the centre is the limit.
        val radius = maxRadiusFromCentre(shortcutForeground)

        assertTrue(
            "the mark reaches $radius dp from the centre of the 108dp viewport; the safe " +
                "circle is $SAFE_RADIUS dp and a launcher may crop the rest",
            radius <= SAFE_RADIUS,
        )
    }

    @Test
    fun `the outline measurement sees curves, relative commands and group transforms`() {
        // Guards the measuring itself. The curve's control point (84,40) is 30.5 from the centre
        // but the curve never gets past its start at 30, so control points must not count.
        val curve = vector("""<path android:pathData="M54,54H84C84,40 64,40 64,54Z" />""")
        assertEquals(30.0, maxRadiusFromCentre(curve), 1e-6)

        val relative = vector("""<path android:pathData="m54,54h10v-20l-5,0z" />""")
        assertEquals(hypot(10.0, 20.0), maxRadiusFromCentre(relative), 1e-6)

        val scaled = vector(
            """<group android:scaleX="0.5" android:scaleY="0.5" android:pivotX="54" android:pivotY="54">
                 <path android:pathData="M54,54H94V64Z" />
               </group>""",
        )
        assertEquals(hypot(20.0, 5.0), maxRadiusFromCentre(scaled), 1e-6)
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

    private fun vector(body: String) = "<vector>$body</vector>"

    /**
     * Largest distance from the viewport centre of any point on the true outline of every `<path>`:
     * path data is parsed (M/L/H/V/C/S/Q/T/Z, absolute and relative), curves are sampled on the
     * curve, and enclosing `<group>` scale/pivot/translate is applied. Rotation and arcs are not
     * supported and fail loudly rather than being skipped.
     */
    private fun maxRadiusFromCentre(vectorXml: String): Double {
        val groups = ArrayDeque<GroupTransform>()
        var worst = -1.0
        for (tag in Regex("""<(/?)(group|path)\b([^>]*)>""").findAll(vectorXml)) {
            val (closing, name, attrs) = tag.destructured
            when {
                name == "group" && closing.isNotEmpty() -> groups.removeLast()
                name == "group" -> groups.addLast(groupTransform(attrs, groups.lastOrNull()))
                else -> {
                    val data = Regex("""android:pathData="([^"]*)"""").find(attrs)?.groupValues?.get(1)
                        ?: error("no pathData in <path $attrs>")
                    val transform = groups.lastOrNull()
                    for ((x, y) in outlinePoints(data)) {
                        val (tx, ty) = transform?.apply(x, y) ?: (x to y)
                        worst = maxOf(worst, hypot(tx - CENTRE, ty - CENTRE))
                    }
                }
            }
        }
        assertTrue("no path outline measured", worst >= 0.0)
        return worst
    }

    private class GroupTransform(
        val scaleX: Double,
        val scaleY: Double,
        val pivotX: Double,
        val pivotY: Double,
        val translateX: Double,
        val translateY: Double,
        val parent: GroupTransform?,
    ) {
        /** A group's own transform applies first, then its parents'. */
        fun apply(x: Double, y: Double): Pair<Double, Double> {
            val tx = scaleX * (x - pivotX) + pivotX + translateX
            val ty = scaleY * (y - pivotY) + pivotY + translateY
            return parent?.apply(tx, ty) ?: (tx to ty)
        }
    }

    private fun groupTransform(attrs: String, parent: GroupTransform?): GroupTransform {
        fun attr(name: String, default: Double) =
            Regex("""android:$name="(-?[\d.]+)(?:dp)?"""").find(attrs)?.groupValues?.get(1)?.toDouble() ?: default
        assertEquals("a group rotation is not measured; extend the test first", 0.0, attr("rotation", 0.0), 0.0)
        return GroupTransform(
            attr("scaleX", 1.0), attr("scaleY", 1.0),
            attr("pivotX", 0.0), attr("pivotY", 0.0),
            attr("translateX", 0.0), attr("translateY", 0.0),
            parent,
        )
    }

    /** Vertices plus points sampled along every curve segment of an SVG-style path. */
    private fun outlinePoints(pathData: String): List<Pair<Double, Double>> {
        val tokens = Regex("""[MmLlHhVvCcSsQqTtZzAa]|-?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?""")
            .findAll(pathData).map { it.value }.toList()
        val points = mutableListOf<Pair<Double, Double>>()
        var i = 0
        var cmd = ' '
        var x = 0.0
        var y = 0.0
        var startX = 0.0
        var startY = 0.0
        var lastCtrl: Pair<Double, Double>? = null // previous curve's last control point, for S/T
        var lastWasCubic = false
        fun num() = tokens[i++].toDouble()

        while (i < tokens.size) {
            if (tokens[i][0].isLetter()) {
                cmd = tokens[i++][0]
            } else if (cmd == 'M') {
                cmd = 'L' // extra coordinate pairs after a moveto are linetos
            } else if (cmd == 'm') {
                cmd = 'l'
            }
            val ox = if (cmd.isLowerCase()) x else 0.0
            val oy = if (cmd.isLowerCase()) y else 0.0
            val upper = cmd.uppercaseChar()
            var cubic = false
            var quad = false
            when (upper) {
                'M' -> { x = ox + num(); y = oy + num(); startX = x; startY = y; points += x to y }
                'L' -> { x = ox + num(); y = oy + num(); points += x to y }
                'H' -> { x = ox + num(); points += x to y }
                'V' -> { y = oy + num(); points += x to y }
                'C', 'S' -> {
                    val from = x to y
                    val c1 = when {
                        upper == 'C' -> (ox + num()) to (oy + num())
                        lastWasCubic -> (2 * x - lastCtrl!!.first) to (2 * y - lastCtrl!!.second)
                        else -> from
                    }
                    val c2 = (ox + num()) to (oy + num())
                    val end = (ox + num()) to (oy + num())
                    points += sample { t -> bezier(t, listOf(from, c1, c2, end)) }
                    lastCtrl = c2; x = end.first; y = end.second; cubic = true
                }
                'Q', 'T' -> {
                    val from = x to y
                    val c1 = when {
                        upper == 'Q' -> (ox + num()) to (oy + num())
                        !lastWasCubic && lastCtrl != null -> (2 * x - lastCtrl!!.first) to (2 * y - lastCtrl!!.second)
                        else -> from
                    }
                    val end = (ox + num()) to (oy + num())
                    points += sample { t -> bezier(t, listOf(from, c1, end)) }
                    lastCtrl = c1; x = end.first; y = end.second; quad = true
                }
                'Z' -> { x = startX; y = startY }
                else -> error("path command '$cmd' is not supported by the outline measurement")
            }
            lastWasCubic = cubic
            if (!cubic && !quad) lastCtrl = null
        }
        return points
    }

    private fun sample(at: (Double) -> Pair<Double, Double>): List<Pair<Double, Double>> =
        (0..CURVE_SAMPLES).map { at(it.toDouble() / CURVE_SAMPLES) }

    /** De Casteljau evaluation of a Bézier of any order at [t]. */
    private fun bezier(t: Double, controls: List<Pair<Double, Double>>): Pair<Double, Double> {
        var pts = controls
        while (pts.size > 1) {
            pts = pts.zipWithNext { a, b ->
                (a.first + (b.first - a.first) * t) to (a.second + (b.second - a.second) * t)
            }
        }
        return pts.single()
    }

    private companion object {
        const val CENTRE = 54.0
        const val SAFE_RADIUS = 33.0
        const val CURVE_SAMPLES = 400
    }
}
