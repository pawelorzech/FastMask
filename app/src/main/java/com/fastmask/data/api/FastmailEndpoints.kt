package com.fastmask.data.api

import java.net.URI

/**
 * `res/xml/network_security_config.xml` cannot reference Kotlin, so it repeats [API_HOST].
 */
object FastmailEndpoints {

    const val API_HOST = "api.fastmail.com"

    /** Fastmail-owned domain; a session may legitimately point at its subdomains. */
    private const val TRUSTED_DOMAIN = "fastmail.com"

    const val SESSION_URL = "https://$API_HOST/jmap/session"

    /** Retrofit insists on a base URL; every call passes its own `@Url`, so this is never requested. */
    const val RETROFIT_BASE_URL = "https://$API_HOST/"

    const val CORE_CAPABILITY = "urn:ietf:params:jmap:core"
    const val MASKED_EMAIL_CAPABILITY = "https://www.fastmail.com/dev/maskedemail"

    val CAPABILITIES = listOf(CORE_CAPABILITY, MASKED_EMAIL_CAPABILITY)

    /**
     * True only for an https URL on Fastmail's own hosts. The session response
     * names the API URL the bearer token is then sent to, so anything else
     * would hand the token to a third party.
     */
    fun isTrustedHttpsUrl(raw: String): Boolean {
        val uri = runCatching { URI(raw) }.getOrNull() ?: return false
        if (!uri.scheme.equals("https", ignoreCase = true)) return false
        val host = uri.host?.lowercase() ?: return false
        return host == API_HOST || host.endsWith(".$TRUSTED_DOMAIN")
    }
}
