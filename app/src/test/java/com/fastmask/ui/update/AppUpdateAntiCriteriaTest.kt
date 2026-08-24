package com.fastmask.ui.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Pins the properties of in-app updates that are easy to erode and expensive to discover in
 * production. Reads the shipped sources, the same way [com.fastmask.privacy.CrashReportingPrivacyTest]
 * and [com.fastmask.i18n.TranslationCompletenessTest] do.
 */
class AppUpdateAntiCriteriaTest {

    private val repoRoot: File by lazy {
        generateSequence(File(System.getProperty("user.dir")!!).absoluteFile) { it.parentFile }
            .first { File(it, "settings.gradle.kts").isFile }
    }

    private val mainSources: List<File> by lazy {
        File(repoRoot, "app/src/main/java").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()
    }

    private val updateSources: List<File> by lazy {
        mainSources.filter { it.path.contains("/ui/update/") || it.path.contains("/data/update/") }
    }

    /**
     * Staying current is not a paid feature. A free user running a version with a bug in it is
     * still a user whose masks may be at stake — the same reasoning that keeps crash reporting
     * out of the Pro gate.
     */
    @Test
    fun `updating is never a Pro feature`() {
        val offenders = updateSources
            .filter { it.readText().contains("ProStatus") || it.readText().contains("ProRepository") }
            .map { it.name }
        assertEquals("the update code must not consult the Pro entitlement: $offenders", emptyList<String>(), offenders)
    }

    /**
     * The update package must not grow a reporting surface. Whether a user takes an update is
     * behavioural data; FastMask declares it collects none, and the dismissed version code lives
     * on the device precisely so that stays true.
     */
    @Test
    fun `the update package reports nothing anywhere`() {
        assertTrue("expected the update package to exist", updateSources.isNotEmpty())

        val forbidden = listOf(
            "Crashlytics",
            "CrashReporter",
            "Retrofit",
            "OkHttp",
            "recordException",
            "setCustomKey",
            "logEvent",
        )
        val offenders = updateSources.flatMap { file ->
            forbidden.filter { file.readText().contains(it) }.map { "${file.name}: $it" }
        }
        assertEquals("the update package must not report anything: $offenders", emptyList<String>(), offenders)
    }

    /**
     * Play Core's types stay behind the seam. `AppUpdateInfo` in particular is a mutable object
     * Play refuses to accept twice, so a Compose layer observing one would recompose against a
     * value that silently expires — and every one of these APIs throws on a device without Play,
     * which is a supported state everywhere except inside the guarded controller.
     */
    @Test
    fun `only the controller touches the Play update SDK`() {
        val offenders = mainSources
            .filter { it.readText().contains("com.google.android.play.core.appupdate") }
            .map { it.name }
        assertEquals(
            "the Play update SDK must stay inside AppUpdateController: $offenders",
            listOf("AppUpdateController.kt"),
            offenders,
        )
    }

    /**
     * The whole point of the seam is that the decision is testable without Play. If the policy
     * ever imports a Play type, the decision table stops being able to describe what ships.
     */
    @Test
    fun `the policy stays free of Play types`() {
        val policy = File(repoRoot, "app/src/main/java/com/fastmask/data/update/AppUpdatePolicy.kt")
        assertTrue("expected AppUpdatePolicy.kt to exist", policy.isFile)
        assertTrue(
            "AppUpdatePolicy must not depend on the Play SDK",
            !policy.readText().contains("com.google.android.play"),
        )
    }

    /**
     * Gating on `BuildConfig.DEBUG` would switch the feature off in exactly the builds used to
     * test it — internal app sharing installs are debuggable and *are* installed by Play. The
     * install source is the honest question: does this app have a Play install to update from.
     */
    @Test
    fun `the check is gated on the install source rather than the build type`() {
        val controller = updateSources.single { it.name == "AppUpdateController.kt" }.readText()
        assertTrue(
            "the controller must gate on the Play install source",
            controller.contains("com.android.vending"),
        )
        assertTrue(
            "the controller must not gate on BuildConfig.DEBUG",
            !controller.contains("BuildConfig.DEBUG"),
        )
    }

    /** The restart prompt must never be composed over the biometric lock screen. */
    @Test
    fun `the restart prompt lives behind the app lock`() {
        val activity = File(repoRoot, "app/src/main/java/com/fastmask/MainActivity.kt").readText()
        val dialogAt = activity.indexOf("UpdateReadyDialog(")
        val navHostAt = activity.indexOf("FastMaskNavHost(")
        assertTrue("MainActivity must show UpdateReadyDialog", dialogAt > 0)
        assertTrue(
            "UpdateReadyDialog must sit in the unlocked branch, after the NavHost",
            dialogAt > navHostAt,
        )
    }
}
