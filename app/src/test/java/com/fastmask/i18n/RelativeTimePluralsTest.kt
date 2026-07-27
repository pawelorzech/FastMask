package com.fastmask.i18n

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the relative-time labels against the bug that shipped in Polish.
 *
 * `time_*_ago` is formatted with `getString(id, count)`, which knows nothing
 * about plural rules. English gets away with it because its translations are
 * abbreviations — "%dm ago", "%dy ago" — and an abbreviation is invariant.
 * Polish spelled two of them out: `%d dni temu` and `%d lat temu`. Those are
 * genitive plurals, correct for 5 and above; the same resources render "1 dni
 * temu" and "3 lat temu", which is what a user actually saw on a mask card.
 *
 * Two ways out, and this file accepts either:
 *
 *  - shorten the translation back to an abbreviation, the form the resource was
 *    designed for ("%d l. temu"); or
 *  - promote the key to `<plurals>` and let `getQuantityString` pick the form.
 *
 * What it does NOT accept is the middle ground that caused the bug: a spelled
 * out unit noun inside a plain `<string>`, in a language that inflects it.
 *
 * Companion to [TranslationCompletenessTest], and deliberately separate: that
 * one only parses `<string>`, so the day these keys become `<plurals>` they
 * would quietly drop out of its coverage.
 */
class RelativeTimePluralsTest {

    private val resDir = File("src/main/res")

    private val keys = listOf(
        "time_min_ago",
        "time_hour_ago",
        "time_day_ago",
        "time_week_ago",
        "time_month_ago",
        "time_year_ago",
    )

    /**
     * CLDR plural categories per shipped locale — the forms `<plurals>` must
     * carry for `getQuantityString` to have an answer for every count.
     *
     * The single-category languages are here so that the table is exhaustive:
     * a new locale directory with no entry fails the tests below rather than
     * being skipped.
     */
    private val pluralForms: Map<String, Set<String>> = mapOf(
        "values" to setOf("one", "other"),
        // Six categories, the widest of the twenty.
        "values-ar" to setOf("zero", "one", "two", "few", "many", "other"),
        // Slavic: one / few (2-4) / many (5+, and the teens) / other (fractions).
        "values-pl" to setOf("one", "few", "many", "other"),
        "values-ru" to setOf("one", "few", "many", "other"),
        "values-uk" to setOf("one", "few", "many", "other"),
        // Singular / plural.
        "values-bn" to setOf("one", "other"),
        "values-de" to setOf("one", "other"),
        "values-es" to setOf("one", "other"),
        "values-fr" to setOf("one", "other"),
        "values-hi" to setOf("one", "other"),
        "values-it" to setOf("one", "other"),
        "values-nl" to setOf("one", "other"),
        "values-pt" to setOf("one", "other"),
        "values-tr" to setOf("one", "other"),
        // No count agreement at all: one form covers every number.
        "values-b+zh+Hans" to setOf("other"),
        "values-id" to setOf("other"),
        "values-ja" to setOf("other"),
        "values-ko" to setOf("other"),
        "values-th" to setOf("other"),
        "values-vi" to setOf("other"),
    )

    /**
     * Unit nouns that a language inflects after a numeral, per locale.
     *
     * Only the locales with MORE THAN TWO plural categories are listed. That is
     * where the trap lives: their "5 and above" form is the one a translator
     * reaches for (it looks like the plural), and it is wrong for 1 and for
     * 2-4. Two-form languages are left out on purpose — their singular/plural
     * split is obvious enough that it does not need a dictionary, and listing
     * every inflection of "minute" in nine more languages would generate false
     * positives faster than it would catch anything.
     *
     * Matching is on whole letter-runs, so an abbreviation ("mies.", "godz.",
     * "%dм") never trips it.
     */
    private val inflectedUnitNouns: Map<String, Set<String>> = mapOf(
        "values-pl" to setOf(
            "minuta", "minuty", "minut", "minutę",
            "godzina", "godziny", "godzin", "godzinę",
            "dzień", "dnia", "dni",
            "tydzień", "tygodnia", "tygodnie", "tygodni",
            "miesiąc", "miesiąca", "miesiące", "miesięcy",
            "rok", "roku", "lata", "lat",
        ),
        "values-ru" to setOf(
            "минута", "минуты", "минут", "минуту",
            "час", "часа", "часов",
            "день", "дня", "дней",
            "неделя", "недели", "недель", "неделю",
            "месяц", "месяца", "месяцев",
            "год", "года", "лет",
        ),
        "values-uk" to setOf(
            "хвилина", "хвилини", "хвилин", "хвилину",
            "година", "години", "годин", "годину",
            "день", "дня", "днів",
            "тиждень", "тижня", "тижні", "тижнів",
            "місяць", "місяця", "місяці", "місяців",
            "рік", "року", "роки", "років",
        ),
        "values-ar" to setOf(
            "دقيقة", "دقيقتان", "دقيقتين", "دقائق",
            "ساعة", "ساعتان", "ساعتين", "ساعات",
            "يوم", "يومان", "يومين", "أيام",
            "أسبوع", "أسبوعان", "أسبوعين", "أسابيع",
            "شهر", "شهران", "شهرين", "أشهر", "شهور",
            "سنة", "سنتان", "سنتين", "سنوات", "سنين",
        ),
    )

    private val stringRegex =
        Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
    private val pluralsRegex =
        Regex("""<plurals name="([^"]+)"[^>]*>(.*?)</plurals>""", RegexOption.DOT_MATCHES_ALL)
    private val itemRegex =
        Regex("""<item quantity="([^"]+)"[^>]*>(.*?)</item>""", RegexOption.DOT_MATCHES_ALL)
    private val formatArg = Regex("""%(\d+\$)?[sdf]""")

    private fun text(dir: String) = File(resDir, "$dir/strings.xml").readText()

    private fun strings(dir: String): Map<String, String> =
        stringRegex.findAll(text(dir)).associate { it.groupValues[1] to it.groupValues[2].trim() }

    /** name -> (quantity -> value). */
    private fun plurals(dir: String): Map<String, Map<String, String>> =
        pluralsRegex.findAll(text(dir)).associate { match ->
            match.groupValues[1] to itemRegex.findAll(match.groupValues[2])
                .associate { it.groupValues[1] to it.groupValues[2].trim() }
        }

    private fun localeDirs(): List<String> =
        (resDir.listFiles() ?: emptyArray())
            .filter { it.isDirectory && (it.name == "values" || it.name.startsWith("values-")) }
            .filter { File(it, "strings.xml").exists() }
            .map { it.name }
            .sorted()

    /** Maximal runs of letters, lower-cased. Digits, `%` and `.` are separators. */
    private fun words(value: String): List<String> =
        value.split(*value.filterNot { it.isLetter() }.toSet().toCharArray())
            .filter { it.isNotBlank() }
            .map { it.lowercase() }

    @Test
    fun `the plural-form table covers every shipped locale`() {
        // Without this, adding values-cs (four categories, same trap as Polish)
        // would silently opt out of every check below.
        val untabled = localeDirs().filterNot { it in pluralForms }
        assertTrue(
            "locale(s) with no entry in pluralForms — add the language's CLDR " +
                "categories before shipping them:\n" + untabled.joinToString("\n").prependIndent("  "),
            untabled.isEmpty(),
        )
    }

    /**
     * The whole point of the exercise: no language that inflects the unit noun
     * may spell it out inside a plain `<string>`.
     *
     * Fails today on `values-pl` (`%d dni temu`, `%d lat temu`) and on
     * `values-ar` (every key carries the singular noun). Either shorten the
     * translation to an abbreviation or move the key to `<plurals>`.
     */
    @Test
    fun `no inflecting locale spells the unit out in a plain string`() {
        val offenders = inflectedUnitNouns.entries.flatMap { (dir, nouns) ->
            val translated = strings(dir)
            keys.mapNotNull { key ->
                val value = translated[key] ?: return@mapNotNull null
                val spelled = words(value).filter { it in nouns }
                if (spelled.isEmpty()) null
                else "$dir: $key = \"$value\" — ${spelled.joinToString()} is inflected by count"
            }
        }

        assertTrue(
            "${offenders.size} relative-time label(s) spell out a unit noun that the " +
                "language inflects, inside a <string> formatted with getString(id, count) — " +
                "so one form is shown for every number:\n" +
                offenders.joinToString("\n").prependIndent("  ") +
                "\n  Fix by shortening to an abbreviation, or by promoting the key to " +
                "<plurals> in ALL locales and switching the call to getQuantityString.",
            offenders.isEmpty(),
        )
    }

    /**
     * A resource name resolves per TYPE. `<string name="time_year_ago">` in
     * values/ and `<plurals name="time_year_ago">` in values-pl/ are two
     * different resources: `R.plurals.time_year_ago` would not even exist for
     * the code to call, and once it did, every locale that stayed a `<string>`
     * would fall back to the DEFAULT plurals — English text for a Russian user.
     * Promotion is all twenty locales or none.
     */
    @Test
    fun `each relative-time key is the same resource type in every locale`() {
        val locales = localeDirs()
        assertTrue("no locale resource directories found", locales.isNotEmpty())

        val problems = keys.flatMap { key ->
            val asString = locales.filter { key in strings(it) }
            val asPlurals = locales.filter { key in plurals(it) }
            val missing = locales.filterNot { it in asString || it in asPlurals }

            buildList {
                if (missing.isNotEmpty()) {
                    add("$key is absent from ${missing.joinToString()}")
                }
                if (asString.isNotEmpty() && asPlurals.isNotEmpty()) {
                    add(
                        "$key is a <string> in ${asString.joinToString()} but a <plurals> in " +
                            "${asPlurals.joinToString()} — the <string> locales will render " +
                            "the default language",
                    )
                }
                locales.filter { it in asString && it in asPlurals }.forEach {
                    add("$key is declared BOTH ways in $it")
                }
            }
        }

        assertTrue(
            "${problems.size} relative-time key(s) are inconsistently declared:\n" +
                problems.joinToString("\n").prependIndent("  "),
            problems.isEmpty(),
        )
    }

    /**
     * `getQuantityString` falls back to `other` for any category a locale does
     * not declare — silently. A Polish `<plurals>` with only `one` and `other`
     * compiles, ships, and reproduces the original bug for 2, 3 and 4.
     */
    @Test
    fun `a plurals resource declares exactly the forms its language uses`() {
        val problems = localeDirs().flatMap { dir ->
            val required = pluralForms[dir] ?: return@flatMap emptyList()
            plurals(dir).filterKeys { it in keys }.flatMap { (key, items) ->
                val declared = items.keys
                buildList {
                    val missing = required - declared
                    if (missing.isNotEmpty()) {
                        add("$dir: $key is missing quantity ${missing.sorted().joinToString()}")
                    }
                    val extra = declared - required
                    if (extra.isNotEmpty()) {
                        // Not an error for aapt, but a form that can never fire
                        // reads as coverage the language does not have.
                        add("$dir: $key declares quantity ${extra.sorted().joinToString()}, unused in this language")
                    }
                }
            }
        }

        assertTrue(
            "${problems.size} plural resource(s) do not match their language's forms:\n" +
                problems.joinToString("\n").prependIndent("  "),
            problems.isEmpty(),
        )
    }

    /**
     * Every form still has to show the number.
     *
     * These are compact labels on a mask card ("3 lata temu"), not sentences —
     * a form that drops `%d` turns into "lata temu", and one that gains a
     * second argument throws at format time.
     */
    @Test
    fun `every plural form takes exactly one number`() {
        val problems = localeDirs().flatMap { dir ->
            plurals(dir).filterKeys { it in keys }.flatMap { (key, items) ->
                items.mapNotNull { (quantity, value) ->
                    val count = formatArg.findAll(value).count()
                    if (count == 1) null
                    else "$dir: $key[$quantity] takes $count format argument(s) — \"$value\""
                }
            }
        }

        assertTrue(
            "${problems.size} plural form(s) do not take exactly one number:\n" +
                problems.joinToString("\n").prependIndent("  "),
            problems.isEmpty(),
        )
    }

    /**
     * The complement of [TranslationCompletenessTest]'s missing-string check,
     * for the type it cannot see.
     */
    @Test
    fun `a plurals key present in one locale is present in all of them`() {
        val locales = localeDirs()
        val declared = locales.flatMap { plurals(it).keys }.toSet()

        val gaps = declared.flatMap { key ->
            locales.filterNot { key in plurals(it) }.map { "$it: $key" }
        }

        assertTrue(
            "${gaps.size} plural resource(s) exist in some locales but not all — those users " +
                "get the default language:\n" + gaps.joinToString("\n").prependIndent("  "),
            gaps.isEmpty(),
        )
    }
}
