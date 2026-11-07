package com.fastmask.domain.model

/**
 * The one rule for a mask's optional email prefix: at most [MAX_LENGTH]
 * characters of lowercase ASCII letters, digits and underscores (the JMAP
 * `emailPrefix` constraint). Length is checked before characters.
 */
object MaskPrefix {

    const val MAX_LENGTH = 64

    private fun isAllowed(c: Char): Boolean = c in 'a'..'z' || c in '0'..'9' || c == '_'

    enum class Error { TOO_LONG, INVALID_CHARS }

    /**
     * What typed [input] keeps: lowercased, with every character outside the
     * allowed set (spaces, punctuation, accented and other non-ASCII letters)
     * dropped. The result always passes the character rule of [validate]; the
     * length is left alone.
     */
    fun sanitize(input: String): String = input.lowercase().filter(::isAllowed)

    /** @return null when [prefix] is acceptable (the empty prefix always is). */
    fun validate(prefix: String): Error? = when {
        prefix.length > MAX_LENGTH -> Error.TOO_LONG
        !prefix.all(::isAllowed) -> Error.INVALID_CHARS
        else -> null
    }
}
