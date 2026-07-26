package com.fastmask.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The login screen's "open Fastmail token settings" shortcut.
 *
 * The destination is pinned here rather than typed inline at the call site,
 * because a wrong path lands the user on a generic page with no idea what to
 * do next — which is the exact friction the shortcut exists to remove.
 *
 * Verified against Fastmail's own developer documentation
 * (fastmail.com/for-developers/integrating-with-fastmail/), which names
 * Settings → Privacy & Security → Manage API tokens at
 * app.fastmail.com/settings/security/tokens.
 */
class FastmailLinksTest {

    @Test
    fun `the token settings link is an https fastmail web app url`() {
        val url = FastmailLinks.TOKEN_SETTINGS_URL

        assertTrue("must be https, never cleartext", url.startsWith("https://"))
        assertTrue(
            "must point at Fastmail's own web app, got: $url",
            url.startsWith("https://app.fastmail.com/"),
        )
    }

    @Test
    fun `the token settings link lands in the security settings section`() {
        assertTrue(
            "must land in Privacy & Security, not the generic settings root",
            FastmailLinks.TOKEN_SETTINGS_URL.startsWith("https://app.fastmail.com/settings/security"),
        )
    }

    @Test
    fun `the token settings link is the verified api tokens page`() {
        assertEquals(
            "https://app.fastmail.com/settings/security/tokens",
            FastmailLinks.TOKEN_SETTINGS_URL,
        )
    }
}
