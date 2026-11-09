package com.fastmask.privacy

import com.fastmask.data.crash.FirebaseCrashlyticsReporter
import com.fastmask.domain.crash.CrashReporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Method
import java.util.zip.ZipFile

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
 * The guard is structural, not a scan for spellings, so it also fails for the
 * *next* person rather than only for the change that introduced Crashlytics:
 *
 *  - [CrashReporter] and its one implementation expose no method beyond the two
 *    data-free switches (reflection on the compiled classes);
 *  - neither seam file compiles a top-level/extension facade class, which is
 *    how data could reach the SDK past a reflection check;
 *  - exactly one compiled class names the Firebase SDK (a bytecode scan sees
 *    Java files, odd paths and extension functions that a source scan misses);
 *  - no analytics, profiling or advertising dependency, declared or
 *    transitive, because "no Google Analytics" is claimed in four documents;
 *  - session reporting is switched off in the manifest.
 *
 * Allowed, for the record: app version, screen name, HTTP status code — values
 * that describe the app, not the person using it.
 *
 * Limit, stated so nobody has to discover it: this works by the SDK's name
 * appearing in a compiled class's constant pool. Reflection that assembles the
 * class name at runtime is invisible to it; that is deliberate evasion, not the
 * drift these guards exist to stop.
 */
class CrashReportingPrivacyTest {

    /** The one file allowed to name the Firebase SDK (used in failure messages). */
    private val seamFile = "src/main/java/com/fastmask/data/crash/FirebaseCrashlyticsReporter.kt"

    /**
     * The failure mode that matters most is a new method on the seam itself: a
     * `fun note(value: String)` on [CrashReporter], implemented as
     * `crashlytics().log(value)`, would stay inside the one allowed file and carry
     * an argument no "user-shaped" regex can recognise. So the shape of the seam
     * is asserted directly. The interface exists to have no data channel; if a
     * signature changes, that is a privacy decision and it has to be made here,
     * next to the policy sentences it would invalidate.
     */
    @Test
    fun `the crash reporter seam exposes no way to pass data`() {
        val signature = { method: Method ->
            "${method.name}(${method.parameterTypes.joinToString { it.simpleName }})"
        }

        val declared = CrashReporter::class.java.declaredMethods
            .filterNot { it.isSynthetic || it.isBridge }
            .map(signature)
            .sorted()

        assertEquals(
            "CrashReporter must stay two data-free switches. Anything that takes a " +
                "String, an object, or a lambda is a channel into a crash report, and " +
                "the privacy policy, the README and the store listing all promise there " +
                "is none. If this is a deliberate change, update those three documents " +
                "first, then this list.",
            listOf("deleteUnsentReports()", "setCollectionEnabled(boolean)"),
            declared,
        )
    }

    private val seamShapeFailure =
        "$seamFile must contain exactly one class with exactly the two CrashReporter " +
            "overrides and no other declarations — anything else there is a path " +
            "from app data to Google. This includes extension functions and property " +
            "setters, which the earlier version of this guard could not see: " +
            "`fun CrashReporter.note(value: String)` in this file compiled to a static " +
            "method on FirebaseCrashlyticsReporterKt, passed every assertion, and shipped " +
            "mask addresses to Crashlytics. If the change is deliberate, README.md " +
            "(\"the CrashReporter seam offers no way to pass data into a report\"), " +
            "docs/privacy.md §11, marketing/copy/en.md and marketing/copy/pl.md all become " +
            "false statements and have to be corrected before this list is."

    /**
     * Neither the seam file nor the interface file may declare anything outside
     * its type.
     *
     * Kotlin puts top-level functions and properties — including extension
     * functions — into a synthesised `<FileName>Kt` class. That class existing
     * *is* the bypass: `fun CrashReporter.note(v: String)` next to the reporter
     * compiles to `FirebaseCrashlyticsReporterKt.note(CrashReporter, String)`,
     * which is neither a method of the interface (so reflection on
     * [CrashReporter] misses it) nor spelled like one (so a name scan misses
     * it). Asserting the facade does not exist states the rule once, in the
     * compiler's own terms, instead of guessing at spellings.
     */
    @Test
    fun `neither crash reporting file declares anything at the top level`() {
        // A facade is named after the FILE, and both seam files are named after their class, so
        // the names are derived from the real classes: renaming a class breaks this at compile
        // time. Renaming only the file would still pass silently (the limit of this check).
        val facades = listOf(
            FirebaseCrashlyticsReporter::class.java.name + "Kt",
            CrashReporter::class.java.name + "Kt",
        ).filter { name ->
            runCatching { Class.forName(name, false, javaClass.classLoader) }.isSuccess
        }

        assertTrue(
            "top-level declarations appeared in the crash reporting seam:\n" +
                facades.joinToString("\n").prependIndent("  ") +
                "\nA top-level or extension function there is exactly how data reaches the " +
                "SDK while every name- and interface-based check stays green. " +
                seamShapeFailure,
            facades.isEmpty(),
        )
    }

    /**
     * The implementation as the JVM sees it, which is the only view an added
     * method cannot dress up. A `var breadcrumb: String` with a setter that
     * calls the SDK declares no `fun` at all; here it shows up as
     * `setBreadcrumb(String)` and fails.
     */
    @Test
    fun `the seam implementation exposes no method beyond the two switches`() {
        val declared = FirebaseCrashlyticsReporter::class.java.declaredMethods
            .filterNot { it.isSynthetic || it.isBridge }
            .map { method -> "${method.name}(${method.parameterTypes.joinToString { it.simpleName }})" }
            .sorted()

        assertEquals(
            seamShapeFailure,
            listOf("deleteUnsentReports()", "setCollectionEnabled(boolean)"),
            declared,
        )
    }

    /**
     * The "one file" promise, checked on compiled bytecode instead of on text.
     *
     * A source scan can only fail on spellings someone anticipated; this one
     * asks the artefact. Every class the app ships is searched for the SDK's
     * binary and dotted names — the forms a call site, a field type, a method
     * descriptor or a `Class.forName` string all leave behind — and exactly one
     * class, the seam, is allowed to contain either. That covers Java sources,
     * files whose names or packages nobody thought of, and the extension
     * function that started this, since `FirebaseCrashlyticsReporterKt` is a
     * different class from `FirebaseCrashlyticsReporter`.
     *
     * Generated `R` classes of the Crashlytics artefact itself sit in the
     * SDK's own package and carry the name for that reason alone; they hold int
     * constants and nothing else.
     */
    @Test
    fun `only the seam class references the crash sdk in compiled code`() {
        val location = CrashReporter::class.java.protectionDomain?.codeSource?.location
        assertTrue(
            "cannot locate the compiled app classes, so this guard would pass " +
                "vacuously — fix the lookup rather than deleting the test",
            location != null,
        )

        val classes = compiledClasses(File(location!!.toURI()))
        assertTrue(
            "no compiled app classes found at $location — this guard would pass vacuously",
            classes.isNotEmpty(),
        )

        val seamClass = "com/fastmask/data/crash/FirebaseCrashlyticsReporter"
        val generatedResourceClass = Regex("""^com/google/firebase/.*/R(\$[A-Za-z0-9_]+)?\.class$""")

        val touching = classes.filterValues { bytes ->
            val constants = String(bytes, Charsets.ISO_8859_1)
            constants.contains("com/google/firebase/crashlytics") ||
                constants.contains("com.google.firebase.crashlytics")
        }.keys.sorted()

        assertTrue(
            "no compiled class references the Crashlytics SDK — the seam is not wired, " +
                "and this guard proves nothing",
            touching.contains("$seamClass.class"),
        )

        val outside = touching.filterNot { name ->
            name == "$seamClass.class" ||
                name.startsWith("$seamClass\$") ||
                generatedResourceClass.matches(name)
        }

        assertTrue(
            "the Crashlytics SDK must be reachable from one class only, but these " +
                "compiled classes reference it:\n" +
                outside.joinToString("\n").prependIndent("  ") +
                "\nREADME.md, docs/privacy.md §11 and both store listings say the SDK is " +
                "called from exactly one file; while this fails, all four are false. " +
                "Note that a Kotlin file's top-level and extension functions compile into " +
                "a separate `<FileName>Kt` class, which is not the seam no matter which " +
                "file they were written in.",
            outside.isEmpty(),
        )
    }

    /** Class name to bytes, from either a classes directory or a classes jar. */
    private fun compiledClasses(location: File): Map<String, ByteArray> = when {
        location.isDirectory -> location.walkTopDown()
            .filter { it.isFile && it.extension == "class" }
            .associate { it.toRelativeString(location).replace(File.separatorChar, '/') to it.readBytes() }

        else -> ZipFile(location).use { jar ->
            jar.entries().asSequence()
                .filter { !it.isDirectory && it.name.endsWith(".class") }
                .associate { entry -> entry.name to jar.getInputStream(entry).use { it.readBytes() } }
        }
    }

    /**
     * README, the privacy policy and both store listings say the same thing:
     * no Google Analytics, no advertising or attribution SDK, no profiling.
     * Nothing enforced it. `implementation("com.google.firebase:firebase-analytics")`
     * is one line, it is what every Firebase guide suggests next to Crashlytics,
     * it switches on event collection and the advertising ID, and the whole
     * suite would have stayed green.
     *
     * The check is on the build file rather than on a resolved configuration
     * because a unit test cannot resolve one — but a direct declaration is
     * exactly how this artefact gets in, and the classpath probe below covers
     * what a build-file scan cannot see.
     */
    @Test
    fun `no analytics or profiling dependency is declared`() {
        val buildFile = File("build.gradle.kts")
        assertTrue("app/build.gradle.kts not found", buildFile.exists())

        // Deliberately not a blanket "firebase-" match: firebase-crashlytics and
        // firebase-bom belong here, and a guard that fails on them would be
        // deleted rather than understood.
        val forbidden = listOf(
            "firebase-analytics",
            "firebase-perf",
            "firebase-inappmessaging",
            "firebase-config",
            "firebase-ml",
            "play-services-measurement",
            "play-services-ads",
            "google-analytics",
        )

        val declared = buildFile.readLines().withIndex().flatMap { (index, line) ->
            if (line.trimStart().startsWith("//")) {
                emptyList()
            } else {
                forbidden.filter { line.contains(it) }.map { "build.gradle.kts:${index + 1}: $it" }
            }
        }

        assertTrue(
            "the app claims no analytics, no advertising SDK and no profiling in " +
                "README.md, docs/privacy.md and both store listings; these " +
                "dependencies contradict that:\n" +
                declared.joinToString("\n").prependIndent("  "),
            declared.isEmpty(),
        )
    }

    /**
     * The transitive half of the guard above: a dependency can arrive without a
     * line in the build file. These classes exist only in the real analytics
     * implementations — `firebase-measurement-connector`, which Crashlytics
     * does pull in, ships the interop interfaces and none of these.
     */
    @Test
    fun `no analytics implementation is on the classpath`() {
        val forbiddenClasses = listOf(
            "com.google.firebase.analytics.FirebaseAnalytics",
            "com.google.android.gms.measurement.internal.AppMeasurementService",
            "com.google.firebase.perf.FirebasePerformance",
            "com.google.android.gms.ads.identifier.AdvertisingIdClient",
        )

        val present = forbiddenClasses.filter { name ->
            runCatching { Class.forName(name, false, javaClass.classLoader) }.isSuccess
        }

        assertTrue(
            "an analytics or advertising SDK reached the classpath transitively:\n" +
                present.joinToString("\n").prependIndent("  ") +
                "\nCheck the dependency that pulled it in, or update the documents that " +
                "promise it is not there.",
            present.isEmpty(),
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
}
