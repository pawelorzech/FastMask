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

    /** Every `strings*.xml` in a values folder, so per-feature string files are held to the same promise. */
    private fun stringsIn(dir: File): Map<String, String> =
        dir.listFiles { f -> f.name.startsWith("strings") && f.name.endsWith(".xml") }
            .orEmpty().sortedBy { it.name }.fold(emptyMap()) { acc, f -> acc + strings(f) }

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
        val base = stringsIn(File(resDir, "values"))
        val locales = localeDirs()
        assertTrue("no locale resource directories found", locales.isNotEmpty())

        val missing = locales.flatMap { dir ->
            val translated = stringsIn(dir)
            base.keys.filterNot { it in translated }.map { "${dir.name}: $it" }
        }

        assertTrue(
            "${missing.size} string(s) exist in values/ but not in every locale:\n" +
                missing.joinToString("\n").prependIndent("  "),
            missing.isEmpty(),
        )
    }

    /**
     * A translation must take the same format arguments as the default.
     *
     * `email_detail_last_message` was once the sentence "Last message: %s" and
     * later became a bare label, with the value rendered separately — but 18
     * locales kept the old form. `stringResource()` is called without
     * arguments, so those users saw a literal "Letzte Nachricht: %s" on the
     * detail screen. Too few arguments is the more dangerous direction: it
     * throws at format time.
     */
    @Test
    fun `translations take the same format arguments as the default`() {
        val base = stringsIn(File(resDir, "values"))
        val formatArg = Regex("""%(\d+\$)?[sdf]""")

        val mismatched = localeDirs().flatMap { dir ->
            val translated = stringsIn(dir)
            base.mapNotNull { (key, english) ->
                val value = translated[key] ?: return@mapNotNull null
                val expected = formatArg.findAll(english).count()
                val actual = formatArg.findAll(value).count()
                if (expected == actual) null
                else "${dir.name}: $key takes $actual argument(s), default takes $expected — \"$value\""
            }
        }

        assertTrue(
            "${mismatched.size} translation(s) disagree with the default on format arguments:\n" +
                mismatched.joinToString("\n").prependIndent("  "),
            mismatched.isEmpty(),
        )
    }

    @Test
    fun `no sentence is left in English`() {
        val base = stringsIn(File(resDir, "values"))

        val untranslated = localeDirs().flatMap { dir ->
            val translated = stringsIn(dir)
            base.filter { (key, english) -> isProse(key, english) && translated[key] == english }
                .map { (key, english) -> "${dir.name}: $key = \"$english\"" }
        }

        assertTrue(
            "${untranslated.size} sentence(s) still carry the English text:\n" +
                untranslated.joinToString("\n").prependIndent("  "),
            untranslated.isEmpty(),
        )
    }

    /**
     * `settings_footer` replaces a Kotlin template that interpolated the
     * version number in the middle of the line — it must keep exactly one
     * format argument (the version) in every locale, the same shape
     * `settings_version` ("Version %s") already has. Zero arguments means
     * the version number silently dropped off the footer; more than one
     * means the resource carries leftover template syntax.
     */
    @Test
    fun `settings_footer takes exactly one format argument in every locale`() {
        val formatArg = Regex("""%(\d+\$)?[sdf]""")
        val allDirs = listOf(File(resDir, "values")) + localeDirs()

        val problems = allDirs.mapNotNull { dir ->
            val value = stringsIn(dir)["settings_footer"] ?: return@mapNotNull null
            val count = formatArg.findAll(value).count()
            if (count == 1) null else "${dir.name}: settings_footer takes $count argument(s) — \"$value\""
        }

        assertTrue(
            "${problems.size} settings_footer translation(s) do not take exactly one format argument:\n" +
                problems.joinToString("\n").prependIndent("  "),
            problems.isEmpty(),
        )
    }

    /**
     * Keeps the "masked mail" accent styling on the hero line without a
     * hardcoded Kotlin join.
     *
     * `login_hero` is the single pre-joined sentence per locale.
     * `login_hero_accent` is NOT joined into it at render time — it exists purely so LoginScreen.kt can `indexOf` it
     * inside `login_hero` and re-apply the accent colour + italic to that
     * exact range. That only works if `login_hero_accent` is present as a
     * verbatim substring of `login_hero` in every locale; if a future
     * translator edits one string without the other, LoginScreen falls back
     * to plain, unaccented text for that locale silently. This test is what
     * turns that silent fallback into a build failure instead.
     */
    @Test
    fun `login_hero contains login_hero_accent verbatim in every locale`() {
        val allDirs = listOf(File(resDir, "values")) + localeDirs()
        val notContained = allDirs.mapNotNull { dir ->
            val translated = stringsIn(dir)
            val hero = translated.getValue("login_hero")
            val accent = translated.getValue("login_hero_accent")
            if (hero.contains(accent)) null
            else "${dir.name}: login_hero_accent \"$accent\" is not a substring of login_hero \"$hero\""
        }
        assertTrue(
            "${notContained.size} locale(s) have a login_hero_accent that is not contained " +
                "verbatim in login_hero — LoginScreen.kt's indexOf lookup will silently fall " +
                "back to unaccented text for these:\n" +
                notContained.joinToString("\n").prependIndent("  "),
            notContained.isEmpty(),
        )
    }
}
