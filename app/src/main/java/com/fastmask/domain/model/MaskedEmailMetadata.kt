package com.fastmask.domain.model

import java.net.URI
import java.util.Locale

object MaskedEmailMetadata {

    /**
     * Fastmail defines `forDomain` as an HTTP(S) origin, not a bare host or a page URL.
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

fun CreateMaskedEmailParams.normalized(): Result<CreateMaskedEmailParams> {
    val origin = normalizedOrigin(forDomain).getOrElse { return Result.failure(it) }
    return Result.success(
        CreateMaskedEmailParams(
            state = state,
            forDomain = origin?.takeIf { it.isNotBlank() },
            description = description?.takeIf { it.isNotBlank() },
            emailPrefix = emailPrefix?.takeIf { it.isNotBlank() },
            url = url?.takeIf { it.isNotBlank() },
        )
    )
}

// Unlike create, empty strings keep their meaning here: they clear the field.
fun UpdateMaskedEmailParams.normalized(): Result<UpdateMaskedEmailParams> {
    val origin = normalizedOrigin(forDomain).getOrElse { return Result.failure(it) }
    return Result.success(copy(forDomain = origin))
}

private fun normalizedOrigin(forDomain: String?): Result<String?> {
    if (forDomain == null) return Result.success(null)
    val origin = MaskedEmailMetadata.normalizeForDomain(forDomain)
        ?: return Result.failure(IllegalArgumentException("Invalid web origin"))
    return Result.success(origin)
}
