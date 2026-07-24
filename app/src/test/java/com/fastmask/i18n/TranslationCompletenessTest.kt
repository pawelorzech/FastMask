package com.fastmask.i18n

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the app's 20-language promise at build time.
 *
 * Three consecutive audit passes added user-facing strings (prefix validation,
 * the no-matches empty state, the discard-changes and sign-out confirmations)
 * and shipped them English-only across all 19 translated locales — every one
 * carried `tools:ignore="MissingTranslation"`, which silenced Lint's own
 * check, so nothing ever failed. A Polish user tapping "Wyloguj" was shown an
 * English confirmation dialog.
 *
 * The test reads the resource XML directly, so the gap fails here rather than
 * at review time.
 */
class TranslationCompletenessTest {

    private val resDir = File("src/main/res")

    private val stringRegex =
        Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)

    private fun strings(file: File): Map<String, String> =
        stringRegex.findAll(file.readText())
            .associate { it.groupValues[1] to it.groupValues[2].trim() }

    private fun localeDirs(): List<File> =
        resDir.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("values-") }
            ?.filter { File(it, "strings.xml").exists() }
            ?.sortedBy { it.name }
            .orEmpty()

    /** Language names are endonyms — "Polski" stays "Polski" in every locale. */
    private fun isEndonym(key: String) = key.startsWith("language_") || key == "app_name"

    /**
     * Prose, as opposed to a label, a brand name or a format token.
     *
     * Short strings are excluded from the still-English check on purpose: "OK",
     * "URL", "Status", "Accent" and "Filter" really are identical in many of
     * the supported languages, and asserting otherwise would produce noise that
     * gets suppressed and then hides the real gaps. Sentences never legitimately
     * survive translation unchanged, and sentences are what the earlier passes
     * shipped untranslated.
     */
    private fun isProse(key: String, english: String) =
        !isEndonym(key) && english.length >= 25 && english.contains(' ')

    @Test
    fun `no locale is missing a string`() {
        val base = strings(File(resDir, "values/strings.xml"))
        val locales = localeDirs()
        assertTrue("no locale resource directories found", locales.isNotEmpty())

        val missing = locales.flatMap { dir ->
            val translated = strings(File(dir, "strings.xml"))
            base.keys.filterNot { it in translated }.map { "${dir.name}: $it" }
        }

        assertTrue(
            "${missing.size} string(s) exist in values/ but not in every locale:\n" +
                missing.joinToString("\n").prependIndent("  "),
            missing.isEmpty(),
        )
    }

    @Test
    fun `no sentence is left in English`() {
        val base = strings(File(resDir, "values/strings.xml"))

        val untranslated = localeDirs().flatMap { dir ->
            val translated = strings(File(dir, "strings.xml"))
            base.filter { (key, english) -> isProse(key, english) && translated[key] == english }
                .map { (key, english) -> "${dir.name}: $key = \"$english\"" }
        }

        assertTrue(
            "${untranslated.size} sentence(s) still carry the English text:\n" +
                untranslated.joinToString("\n").prependIndent("  "),
            untranslated.isEmpty(),
        )
    }
}
