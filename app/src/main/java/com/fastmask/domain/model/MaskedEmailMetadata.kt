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

/**
 * The create parameters as they are stored, so every repository (the real one
 * and the demo) applies the same rules: `forDomain` becomes a normalised
 * origin, and a blank field means "not supplied" (null). Fails with
 * [IllegalArgumentException] when `forDomain` is not a valid web origin.
 */
fun CreateMaskedEmailParams.normalized(): Result<CreateMaskedEmailParams> {
    val origin = forDomain?.let { raw ->
        MaskedEmailMetadata.normalizeForDomain(raw)
            ?: return Result.failure(IllegalArgumentException("Invalid web origin"))
    }
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

/**
 * The update parameters with `forDomain` normalised. Unlike create, empty
 * strings keep their meaning here: they clear the field (see [UpdateMaskedEmailParams.clearsUrl]).
 * Fails with [IllegalArgumentException] when `forDomain` is not a valid web origin.
 */
fun UpdateMaskedEmailParams.normalized(): Result<UpdateMaskedEmailParams> {
    val origin = forDomain?.let { raw ->
        MaskedEmailMetadata.normalizeForDomain(raw)
            ?: return Result.failure(IllegalArgumentException("Invalid web origin"))
    }
    return Result.success(copy(forDomain = origin))
}
