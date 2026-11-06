package com.fastmask.i18n

import com.fastmask.domain.model.Language
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Binds [Language] to the three places that must agree on the supported
 * languages: the enum, the `values-<qualifier>` resource folders that carry a
 * translation, and `locales_config.xml` (what the system per-app language
 * picker offers). Adding a language in one place only used to fail silently.
 */
class LanguageListTest {

    private val resDir = File("src/main/res")

    /** `zh-Hans` lives in `values-b+zh+Hans`; plain tags are `values-<tag>`. */
    private fun valuesDirName(code: String) =
        if ('-' in code) "values-b+" + code.replace('-', '+') else "values-$code"

    private fun translatedLocaleDirs(): Set<String> =
        resDir.listFiles()
            .orEmpty()
            .filter { it.isDirectory && it.name.startsWith("values-") }
            .filter { File(it, "strings.xml").exists() }
            .map { it.name }
            .toSet()

    private fun localesConfigCodes(): Set<String> =
        Regex("""<locale\s+android:name="([^"]+)"""")
            .findAll(File(resDir, "xml/locales_config.xml").readText())
            .map { it.groupValues[1] }
            .toSet()

    @Test
    fun `Language entries match the translated resource folders`() {
        // English is the base `values/` folder, so it has no qualifier dir.
        val expected = Language.entries
            .filter { it != Language.ENGLISH }
            .map { valuesDirName(it.code) }
            .toSet()
        assertEquals(expected, translatedLocaleDirs())
    }

    @Test
    fun `Language entries match locales_config`() {
        assertEquals(Language.entries.map { it.code }.toSet(), localesConfigCodes())
    }
}
