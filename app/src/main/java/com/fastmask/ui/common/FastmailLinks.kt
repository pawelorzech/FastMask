package com.fastmask.ui.common

/**
 * Outbound links to Fastmail's own web UI, used by the token setup steps on
 * the login screen.
 * Verified against Fastmail's developer docs page
 * `fastmail.com/for-developers/integrating-with-fastmail/`, which points
 * users to Settings -> Privacy & Security -> Manage API tokens at the URL
 * below.
 */
object FastmailLinks {

    /** Where the user creates an API token with the Masked Email scope. */
    const val TOKEN_SETTINGS_URL = "https://app.fastmail.com/settings/security/tokens"
}
