package com.fastmask.privacy

import org.junit.Assert.assertEquals
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

    /**
     * Every shipped source set, not just `main`. A Crashlytics call added under
     * `src/debug/java` or `src/release/java` — or under `src/main/kotlin`, which
     * the build accepts — was invisible to this guard while it walked
     * `src/main/java` alone.
     */
    private fun sources(): List<File> = File("src").walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .filterNot { file ->
            val path = file.invariantSeparatorsPath
            path.startsWith("src/test/") || path.startsWith("src/androidTest/")
        }
        .toList()

    /** Names that carry, or plausibly carry, something belonging to the user. */
    private val userShaped = Regex(
        """(?i)(e-?mail|address|mask|token|domain|description|\burl\b|prefix|account|user_?(name|id))"""
    )

    private val setUserId = Regex("""\bsetUserId\s*\(""")
    private val setCustomKey = Regex("""\bsetCustomKeys?\s*\(([^)]*)\)""")
    private val crashlyticsLog = Regex("""(?i)crashlytics[\w.()\s]*\.\s*log\s*\(([^)]*)\)""")

    /**
     * The SDK type itself, as distinct from this app's wrapper around it.
     * A plain `contains("FirebaseCrashlytics")` also matched
     * `FirebaseCrashlyticsReporter`, which is why the DI module had to be
     * exempted; the exemption then let any file under `di/` call the SDK
     * directly. Matching the package and the bare class name instead means the
     * README's claim — one file, and only one — is what the guard enforces.
     */
    private val sdkReference = Regex("""com\.google\.firebase\.crashlytics|\bFirebaseCrashlytics\b""")

    /** The one file allowed to name the Firebase SDK. */
    private val seamFile = "src/main/java/com/fastmask/data/crash/FirebaseCrashlyticsReporter.kt"

    private fun isSeamFile(file: File): Boolean =
        file.invariantSeparatorsPath == seamFile

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
            "no Crashlytics call site found under src/ — this guard would pass " +
                "vacuously; wire the reporter or delete the guard deliberately",
            callSites.isNotEmpty(),
        )
    }

    /**
     * Exactly one file, which is what README.md and the privacy policy claim.
     * The guard used to exempt the whole `di/` package, so any new file there
     * could have called the SDK directly while both documents kept saying
     * otherwise.
     */
    @Test
    fun `the firebase sdk is reachable only through the crash reporter seam`() {
        val referencing = sources().filter { sdkReference.containsMatchIn(it.readText()) }

        assertTrue(
            "no file references FirebaseCrashlytics — the CrashReporter seam is not implemented",
            referencing.isNotEmpty(),
        )

        val paths = referencing.map { it.invariantSeparatorsPath }
        assertEquals(
            "the Firebase SDK must be named in exactly one file, and that file is $seamFile",
            listOf(seamFile),
            paths.sorted(),
        )

        val outside = referencing.filterNot { isSeamFile(it) }.map { it.invariantSeparatorsPath }
        assertTrue(
            "the Firebase SDK must stay behind CrashReporter, but it is named in:\n" +
                outside.joinToString("\n").prependIndent("  "),
            outside.isEmpty(),
        )
    }

    /**
     * `firebase-crashlytics` pulls in `firebase-sessions`, which posts a session
     * event to Google on every cold start and every foreground — no crash
     * involved. The privacy policy says data goes to Google "when the app
     * crashes"; this flag is what makes that sentence true. Deleting it turns
     * the policy into a false statement, silently.
     */
    @Test
    fun `session reporting is switched off in the manifest`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()

        val declaration = Regex(
            """<meta-data\s+android:name="firebase_sessions_enabled"\s+android:value="false"\s*/>"""
        )
        assertTrue(
            "src/main/AndroidManifest.xml must declare firebase_sessions_enabled=false, " +
                "otherwise the app pings Google on every launch and the privacy policy is wrong",
            declaration.containsMatchIn(manifest),
        )
    }

    /**
     * Crashlytics only reports what it was switched on for. If nothing applies
     * the stored preference at startup, a user who opted out keeps being
     * reported on until they open Settings again.
     *
     * The assertion is on the *call*, not on the word appearing somewhere in
     * the file. The previous version checked for the substring "CrashReporting",
     * which the import line satisfies on its own — deleting the whole startup
     * invocation left this test green.
     */
    @Test
    fun `the app applies the stored crash reporting preference on startup`() {
        val application = File(sourceRoot, "com/fastmask/FastMaskApplication.kt")
        assertTrue("FastMaskApplication.kt not found", application.exists())
        val source = application.readText()

        val declarationAndCall = Regex("""\bapplyCrashReportingPreference\s*\(\s*\)""")
            .findAll(source)
            .count()
        assertTrue(
            "applyCrashReportingPreference() must be declared AND called from onCreate — " +
                "found $declarationAndCall occurrence(s)",
            declarationAndCall >= 2,
        )

        assertTrue(
            "the startup path must delegate to CrashReportingStartup, which is the " +
                "collaborator unit tests actually cover",
            Regex("""\bcrashReportingStartup\s*\.\s*apply\s*\(""").containsMatchIn(source),
        )

        // onCreate is where it has to happen; a call left in a private method
        // nobody invokes would satisfy the count above.
        val onCreateBody = Regex(
            """override fun onCreate\(\)\s*\{(.*?)\n    \}""",
            RegexOption.DOT_MATCHES_ALL,
        ).find(source)?.groupValues?.get(1)
        assertTrue(
            "onCreate() must call applyCrashReportingPreference(); body was:\n$onCreateBody",
            onCreateBody?.contains("applyCrashReportingPreference()") == true,
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
