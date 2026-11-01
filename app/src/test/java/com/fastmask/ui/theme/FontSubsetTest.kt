package com.fastmask.ui.theme

import java.io.File
import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bundled Google Sans Flex file is a subset of the 3.9 MB full variable font
 * (`tools/brand/subset-font.ts`). Without it the AAB grows by ~3.6 MB for axes the
 * app never drives. This reads the sfnt `fvar` table directly, so it needs no
 * Android APIs.
 */
class FontSubsetTest {

    private val font = File("src/main/res/font/google_sans_flex.ttf")

    private data class Axis(val tag: String, val min: Float, val default: Float, val max: Float)

    private fun fixed(raw: Int) = raw / 65536f

    private fun axes(): List<Axis> {
        val b = ByteBuffer.wrap(font.readBytes()) // big-endian, like sfnt
        val numTables = b.getShort(4).toInt() and 0xFFFF
        var fvar = -1
        for (i in 0 until numTables) {
            val rec = 12 + i * 16
            val tag = String(ByteArray(4) { b.get(rec + it) }, Charsets.US_ASCII)
            if (tag == "fvar") fvar = b.getInt(rec + 8)
        }
        assertTrue("font has no fvar table", fvar >= 0)
        val axesOffset = b.getShort(fvar + 4).toInt() and 0xFFFF
        val axisCount = b.getShort(fvar + 8).toInt() and 0xFFFF
        val axisSize = b.getShort(fvar + 10).toInt() and 0xFFFF
        return (0 until axisCount).map { i ->
            val rec = fvar + axesOffset + i * axisSize
            Axis(
                tag = String(ByteArray(4) { b.get(rec + it) }, Charsets.US_ASCII),
                min = fixed(b.getInt(rec + 4)),
                default = fixed(b.getInt(rec + 8)),
                max = fixed(b.getInt(rec + 12)),
            )
        }
    }

    @Test
    fun subsetFontIsSmallerThan1_5MB() {
        assertTrue("font file missing: ${font.absolutePath}", font.isFile)
        assertTrue("font is ${font.length()} bytes", font.length() < 1_500_000)
    }

    @Test
    fun subsetFontKeepsTheWeightAndRoundnessAxes() {
        val byTag = axes().associateBy { it.tag }
        val wght = byTag["wght"]
        val rond = byTag["ROND"]
        assertTrue("wght axis missing", wght != null)
        assertTrue("ROND axis missing", rond != null)
        // Type.kt drives weights 400..700 and roundness 0 / 100.
        assertTrue(wght!!.min <= 400f && wght.max >= 700f)
        assertTrue(rond!!.min <= 0f && rond.max >= 100f)
    }

    @Test
    fun subsetFontPinsTheAxesTheAppNeverDrives() {
        assertEquals(setOf("wght", "ROND"), axes().map { it.tag }.toSet())
    }
}
