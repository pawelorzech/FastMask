package com.fastmask.privacy

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A build-time guard on the promise the app makes on its store listing and in
 * its README: crash diagnostics, never behaviour analytics, and nothing that
 * identifies a person or their data.
 *
 * Crashlytics makes the wrong thing easy. `setUserId`, `setCustomKey` and
 * `log` are one-liners, they are what every tutorial reaches for, and each of
 * them ships whatever you hand it straight to Google. In this app the values
 * closest to hand are exactly the ones that must never leave the device: the
 * mask address, the mask description, the domain or URL a mask was made for,
 * the user's own e-mail address, and the Fastmail API token.
 *
 * This test reads the shipped sources instead of trusting review, the same way
 * `TranslationCompletenessTest` reads the resource XML. It is written to fail
 * for the *next* person as much as for the change that introduced Crashlytics:
 *
 *  - any `setUserId(` anywhere in the app, unconditionally — there is no
 *    correct use of it here;
 *  - any `setCustomKey(` or Crashlytics `log(` whose arguments mention
 *    something user-shaped (email, address, mask, token, domain, description,
 *    url, prefix, account, username);
 *  - any reference to the Firebase Crashlytics SDK outside the one file that
 *    owns the seam, so new call sites cannot appear in a ViewModel or a
 *    repository where user data is in scope.
 *
 * Allowed, for the record: app version, screen name, HTTP status code — values
 * that describe the app, not the person using it.
 */
class CrashReportingPrivacyTest {

    private val sourceRoot = File("src/main/java")

    private fun sources(): List<File> =
        sourceRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    /** Names that carry, or plausibly carry, something belonging to the user. */
    private val userShaped = Regex(
        """(?i)(e-?mail|address|mask|token|domain|description|\burl\b|prefix|account|user_?(name|id))"""
    )

    private val setUserId = Regex("""\bsetUserId\s*\(""")
    private val setCustomKey = Regex("""\bsetCustomKeys?\s*\(([^)]*)\)""")
    private val crashlyticsLog = Regex("""(?i)crashlytics[\w.()\s]*\.\s*log\s*\(([^)]*)\)""")

    /** Files allowed to name the Firebase SDK: the seam and its Hilt wiring. */
    private fun isSeamFile(file: File): Boolean {
        val path = file.invariantSeparatorsPath
        return path.contains("/data/crash/") || path.contains("/di/")
    }

    private fun violations(): List<String> = sources().flatMap { file ->
        file.readLines().withIndex().flatMap { (index, line) ->
            val where = "${file.invariantSeparatorsPath}:${index + 1}"
            buildList {
                if (setUserId.containsMatchIn(line)) {
                    add("$where: setUserId is never allowed — $line")
                }
                setCustomKey.findAll(line).forEach { match ->
                    val args = match.groupValues[1]
                    userShaped.find(args)?.let {
                        add("$where: custom key carries user data (\"${it.value}\") — $line")
                    }
                }
                crashlyticsLog.findAll(line).forEach { match ->
                    val args = match.groupValues[1]
                    userShaped.find(args)?.let {
                        add("$where: crash log carries user data (\"${it.value}\") — $line")
                    }
                }
            }
        }
    }

    @Test
    fun `no crash report ever carries user data`() {
        val found = violations()

        assertTrue(
            "${found.size} call(s) would send user data to the crash backend:\n" +
                found.joinToString("\n").prependIndent("  "),
            found.isEmpty(),
        )

        // A scan that finds no Crashlytics at all passes vacuously and proves
        // nothing, so the guard also asserts there is something to guard.
        val callSites = sources().filter { it.readText().contains("Crashlytics") }
        assertTrue(
            "no Crashlytics call site found in src/main/java — this guard would pass " +
                "vacuously; wire the reporter or delete the guard deliberately",
            callSites.isNotEmpty(),
        )
    }

    @Test
    fun `the firebase sdk is reachable only through the crash reporter seam`() {
        val referencing = sources().filter { it.readText().contains("FirebaseCrashlytics") }

        assertTrue(
            "no file references FirebaseCrashlytics — the CrashReporter seam is not implemented",
            referencing.isNotEmpty(),
        )

        val outside = referencing.filterNot { isSeamFile(it) }.map { it.invariantSeparatorsPath }
        assertTrue(
            "the Firebase SDK must stay behind CrashReporter, but it is named in:\n" +
                outside.joinToString("\n").prependIndent("  "),
            outside.isEmpty(),
        )
    }

    /**
     * Crashlytics only reports what it was switched on for. If nothing applies
     * the stored preference at startup, a user who opted out keeps being
     * reported on until they open Settings again.
     */
    @Test
    fun `the app applies the stored crash reporting preference on startup`() {
        val application = File(sourceRoot, "com/fastmask/FastMaskApplication.kt")
        assertTrue("FastMaskApplication.kt not found", application.exists())

        assertTrue(
            "FastMaskApplication does not apply the crash reporting preference on start",
            application.readText().contains("CrashReporting"),
        )
    }

    /**
     * The switch needs a label and an explanation, and both must exist in the
     * default locale before `TranslationCompletenessTest` can hold the other 19
     * to the same standard.
     */
    @Test
    fun `the crash reporting switch has user-facing strings`() {
        val strings = File("src/main/res/values/strings.xml").readText()

        val missing = listOf(
            "settings_crash_reporting",
            "settings_crash_reporting_description",
        ).filterNot { strings.contains("""<string name="$it"""") }

        assertTrue(
            "missing string resource(s): ${missing.joinToString()}",
            missing.isEmpty(),
        )
    }
}
