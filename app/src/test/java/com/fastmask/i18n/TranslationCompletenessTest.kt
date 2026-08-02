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
        val base = strings(File(resDir, "values/strings.xml"))
        val formatArg = Regex("""%(\d+\$)?[sdf]""")

        val mismatched = localeDirs().flatMap { dir ->
            val translated = strings(File(dir, "strings.xml"))
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

    /**
     * gh-29 — "Strings assembled in Kotlin break ja/zh/ar rendering".
     *
     * Two of the three sites in that ticket require brand-new resource keys
     * (the third, `create_email_created`, already exists — that site's fix is
     * a call-site change covered by [com.fastmask.i18n.StringAssemblyTest]):
     *
     *  - `login_hero` — LoginScreen currently builds its hero line by
     *    `append`-ing `login_hero_prefix`, a literal `" "`, then
     *    `login_hero_accent`. That hardcoded space is wrong for ja/zh (no
     *    inter-word spacing) and for ar (it separates the proclitic لـ from
     *    the word it must stay attached to). The fix is ONE resource per
     *    locale holding the two fragments already joined the way that
     *    locale actually joins them.
     *  - `settings_footer` — SettingsScreen currently builds
     *    `"FastMask · ${stringResource(R.string.settings_version, ...)}"` as
     *    a Kotlin template, whose left-to-right concatenation order cannot
     *    be controlled from Kotlin once bidi (Arabic) is involved. The fix
     *    moves the whole composed line into one resource with a positional
     *    placeholder for the version number.
     *
     * Neither key exists anywhere today, so this fails until the fix adds
     * both to `values/strings.xml` and every `values-<locale>` folder. Once
     * added, [translations take the same format arguments as the default]
     * above automatically extends to cover them too.
     */
    @Test
    fun `gh-29 hero and footer resources exist in the default locale and every translated locale`() {
        val newKeys = setOf("login_hero", "settings_footer")
        val base = strings(File(resDir, "values/strings.xml"))
        val locales = localeDirs()
        assertTrue("no locale resource directories found", locales.isNotEmpty())

        val missingFromBase = newKeys.filterNot { it in base }
        assertTrue(
            "gh-29 key(s) missing from the default locale (values/strings.xml) — the fix " +
                "must introduce them there before translating:\n" +
                missingFromBase.joinToString("\n").prependIndent("  "),
            missingFromBase.isEmpty(),
        )

        val missingFromLocales = locales.flatMap { dir ->
            val translated = strings(File(dir, "strings.xml"))
            newKeys.filterNot { it in translated }.map { "${dir.name}: $it" }
        }
        assertTrue(
            "${missingFromLocales.size} gh-29 key(s) missing from a translated locale " +
                "(${locales.size} locale dirs checked):\n" +
                missingFromLocales.joinToString("\n").prependIndent("  "),
            missingFromLocales.isEmpty(),
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
            val value = strings(File(dir, "strings.xml"))["settings_footer"] ?: return@mapNotNull null
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
     * gh-29 follow-up — restoring the "masked mail" accent styling without
     * reintroducing the hardcoded Kotlin join.
     *
     * `login_hero` is the single pre-joined sentence per locale (see the
     * gh-29 test above). `login_hero_accent` is NOT joined into it at
     * render time — it exists purely so LoginScreen.kt can `indexOf` it
     * inside `login_hero` and re-apply the accent colour + italic to that
     * exact range. That only works if `login_hero_accent` is present as a
     * verbatim substring of `login_hero` in every locale; if a future
     * translator edits one string without the other, LoginScreen falls back
     * to plain, unaccented text for that locale silently. This test is what
     * turns that silent fallback into a build failure instead.
     */
    @Test
    fun `login_hero contains login_hero_accent verbatim in every locale`() {
        val keys = listOf("login_hero", "login_hero_accent")
        val allDirs = listOf(File(resDir, "values")) + localeDirs()
        assertTrue("no locale resource directories found", allDirs.size > 1)

        val missingKeys = allDirs.flatMap { dir ->
            val translated = strings(File(dir, "strings.xml"))
            keys.filterNot { it in translated }.map { "${dir.name}: missing $it" }
        }
        assertTrue(
            "${missingKeys.size} locale(s) are missing login_hero and/or login_hero_accent:\n" +
                missingKeys.joinToString("\n").prependIndent("  "),
            missingKeys.isEmpty(),
        )

        val notContained = allDirs.mapNotNull { dir ->
            val translated = strings(File(dir, "strings.xml"))
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
