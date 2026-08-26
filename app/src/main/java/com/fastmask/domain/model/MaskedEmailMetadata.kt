package com.fastmask.domain.model

import java.net.URI
import java.util.Locale

/** Fastmail Masked Email metadata rules that are shared by create and edit flows. */
object MaskedEmailMetadata {

    /**
     * Fastmail defines `forDomain` as an HTTP(S) origin, not a bare host or a page URL.
     * Accept the convenient forms users paste, then store only scheme + host + optional port.
     */
    fun normalizeForDomain(raw: String): String? {
        val value = raw.trim()
        if (value.isEmpty()) return ""

        val candidate = if (SCHEME.matchesAt(value, 0)) value else "https://$value"
        val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if (scheme != "http" && scheme != "https") return null
        if (uri.userInfo != null) return null
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null

        return runCatching {
            URI(scheme, null, host, uri.port, null, null, null).toASCIIString()
        }.getOrNull()
    }

    private val SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]*://")
}
