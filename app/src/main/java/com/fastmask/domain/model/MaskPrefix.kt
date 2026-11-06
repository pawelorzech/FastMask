package com.fastmask.domain.model

/**
 * The one rule for a mask's optional email prefix: at most [MAX_LENGTH]
 * characters of lowercase ASCII letters, digits and underscores (the JMAP
 * `emailPrefix` constraint). Length is checked before characters.
 */
object MaskPrefix {

    const val MAX_LENGTH = 64

    private val ALLOWED = Regex("^[a-z0-9_]*$")

    enum class Error { TOO_LONG, INVALID_CHARS }

    /** @return null when [prefix] is acceptable (the empty prefix always is). */
    fun validate(prefix: String): Error? = when {
        prefix.length > MAX_LENGTH -> Error.TOO_LONG
        !ALLOWED.matches(prefix) -> Error.INVALID_CHARS
        else -> null
    }
}
