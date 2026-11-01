package com.fastmask.ui.settings

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The OFL (Google Sans Flex font) and Apache 2.0 (Material Symbols "alternate_email"
 * glyph in the launcher icon) require their license text to travel with the app.
 * The in-app copies in res/raw must be the repository's authoritative files.
 */
class BundledLicensesTest {

    private val raw = File("src/main/res/raw")
    private val licenses = File("../licenses")

    private fun same(rawName: String, licenseName: String) {
        val inApp = File(raw, rawName)
        val source = File(licenses, licenseName)
        assertTrue("$rawName is missing from res/raw", inApp.exists())
        assertTrue("licenses/$licenseName is missing", source.exists())
        assertEquals(
            "res/raw/$rawName drifted from licenses/$licenseName",
            source.readText().replace("\r\n", "\n"),
            inApp.readText().replace("\r\n", "\n"),
        )
    }

    @Test
    fun fontLicenseIsBundledVerbatim() = same("google_sans_flex_ofl.txt", "GoogleSansFlex-OFL.txt")

    @Test
    fun apacheLicenseIsBundledVerbatim() = same("apache_2_0.txt", "Apache-2.0.txt")

    @Test
    fun bundledTextsAreTheRealLicenses() {
        assertTrue(File(raw, "google_sans_flex_ofl.txt").readText().contains("SIL OPEN FONT LICENSE Version 1.1"))
        assertTrue(File(raw, "apache_2_0.txt").readText().contains("Apache License"))
    }
}
