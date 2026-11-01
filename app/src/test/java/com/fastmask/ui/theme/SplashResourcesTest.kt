package com.fastmask.ui.theme

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The cold-start splash is the first frame users see and cannot be covered by a
 * Compose test, so its resources are checked directly (same approach as
 * TranslationCompletenessTest). Device audit: it still showed the retired navy
 * "Privacy Fold" envelope, and a dark-mode launch flashed a light screen.
 */
class SplashResourcesTest {

    private val res = File("src/main/res")

    private fun splashStyle(dir: String): String? =
        File(res, "$dir/themes.xml").takeIf { it.exists() }?.readText()
            ?.let { Regex("""<style name="Theme\.FastMask\.Splash".*?</style>""", RegexOption.DOT_MATCHES_ALL).find(it)?.value }

    private fun item(style: String, name: String): String? =
        Regex("""<item name="$name">(.*?)</item>""").find(style)?.groupValues?.get(1)

    private val splashDirs = listOf("values", "values-v31", "values-night", "values-night-v31")

    @Test
    fun everySplashVariantUsesTheCurrentLauncherMark() {
        val icons = splashDirs.mapNotNull { dir ->
            splashStyle(dir)?.let { item(it, "windowSplashScreenAnimatedIcon") }
        }
        assertTrue("splash styles found: $icons", icons.isNotEmpty())
        icons.forEach { assertEquals("@drawable/ic_launcher_foreground", it) }
    }

    @Test
    fun darkModeHasItsOwnSplashBackground() {
        listOf("values-night", "values-night-v31").forEach { dir ->
            val style = splashStyle(dir)
            assertTrue("$dir/themes.xml must define Theme.FastMask.Splash", style != null)
            val background = item(style!!, "windowSplashScreenBackground")
            assertTrue("$dir splash background must be set, was $background", !background.isNullOrBlank())
        }
    }
}
