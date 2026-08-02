package com.fastmask.i18n

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * gh-29 — "Strings assembled in Kotlin break ja/zh/ar rendering".
 *
 * Three screens assemble user-facing text with Kotlin string operations
 * instead of letting a resource own the whole string. That works for
 * space-delimited, left-to-right languages and silently breaks for scripts
 * with different joining/ordering rules:
 *
 *  - ui/auth/LoginScreen.kt — `append(" ")` between two translated hero
 *    fragments. ja/zh have no inter-word space; ar's proclitic لـ must stay
 *    attached to the following word, which a hardcoded space prevents.
 *  - ui/list/MaskedEmailListScreen.kt — `.replace("%s", email)` hand-rolled
 *    substitution, bypassing lint's own placeholder checking.
 *  - ui/settings/SettingsScreen.kt — `"FastMask · ${...}"` Kotlin template;
 *    bidi ordering in Arabic cannot be controlled from Kotlin string
 *    concatenation.
 *
 * This test reads the three files as text and looks for the exact offending
 * construct at each site — not a generic pattern — so it cannot pass without
 * that specific line having actually been fixed, and it does not fire on
 * unrelated code elsewhere in the file.
 *
 * Companion to [TranslationCompletenessTest], which checks the resource side
 * (the new/reused string keys); this one checks the Kotlin call sites are no
 * longer doing the assembly themselves.
 */
class StringAssemblyTest {

    private val srcDir = File("src/main/java/com/fastmask")

    private fun read(relativePath: String): String {
        val file = File(srcDir, relativePath)
        assertTrue("expected source file not found: ${file.path}", file.exists())
        return file.readText()
    }

    /**
     * Fails today: LoginScreen.kt:121 is `append(" ")`, sandwiched between
     * `append(heroPrefix)` and `append(heroAccent)`. The fix replaces the
     * three-fragment-plus-hardcoded-space build with ONE combined resource
     * per locale (see [TranslationCompletenessTest]'s `login_hero` check),
     * so no literal single-space `append` call should remain in this file.
     */
    @Test
    fun `LoginScreen no longer joins hero fragments with a hardcoded space`() {
        val source = read("ui/auth/LoginScreen.kt")

        assertFalse(
            "LoginScreen.kt still contains append(\" \") — the hardcoded join between " +
                "login_hero_prefix and login_hero_accent that breaks ja/zh (no inter-word " +
                "space) and ar (separates the proclitic from its word). Replace the join " +
                "with a single pre-joined resource per locale instead of assembling it here.",
            source.contains("""append(" ")"""),
        )
    }

    /**
     * Fails today: MaskedEmailListScreen.kt:222 is
     * `createdMessageTemplate.replace("%s", email)`. The fix calls
     * `stringResource(R.string.create_email_created, email)` directly, which
     * already exists as a resource with a proper `%s` placeholder and is
     * covered by lint's placeholder checking (which the hand-rolled
     * `.replace` bypasses entirely).
     */
    @Test
    fun `MaskedEmailListScreen no longer hand-rolls the placeholder substitution`() {
        val source = read("ui/list/MaskedEmailListScreen.kt")

        assertFalse(
            "MaskedEmailListScreen.kt still contains .replace(\"%s\" — a hand-rolled " +
                "placeholder substitution that bypasses lint's MissingFormatArgument / " +
                "StringFormatCount checks. Call stringResource(R.string.create_email_created, " +
                "email) directly instead.",
            source.contains(".replace(\"%s\""),
        )
    }

    /**
     * Fails today: SettingsScreen.kt:415 is
     * `"FastMask · ${stringResource(R.string.settings_version, ...)}"`. The
     * fix moves the whole composed line — brand name, separator and version
     * — into one resource with a positional placeholder (see
     * [TranslationCompletenessTest]'s `settings_footer` check), because a
     * Kotlin string template cannot reorder for Arabic bidi.
     */
    @Test
    fun `SettingsScreen no longer builds the footer line with a Kotlin template`() {
        val source = read("ui/settings/SettingsScreen.kt")

        assertFalse(
            "SettingsScreen.kt still contains the \"FastMask · \${...}\" Kotlin template — " +
                "its left-to-right concatenation order cannot be controlled from Kotlin once " +
                "Arabic bidi is involved. Move the whole line into a resource with a " +
                "positional placeholder for the version number.",
            source.contains("\"FastMask · \$"),
        )
    }
}
