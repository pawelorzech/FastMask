package com.fastmask.domain.model

/** The JMAP `emailPrefix` constraint: at most [MAX_LENGTH] lowercase ASCII letters, digits and underscores. */
object MaskPrefix {

    const val MAX_LENGTH = 64

    private fun isAllowed(c: Char): Boolean = c in 'a'..'z' || c in '0'..'9' || c == '_'

    enum class Error { TOO_LONG, INVALID_CHARS }

    fun sanitize(input: String): String = input.lowercase().filter(::isAllowed)

    fun validate(prefix: String): Error? = when {
        prefix.length > MAX_LENGTH -> Error.TOO_LONG
        !prefix.all(::isAllowed) -> Error.INVALID_CHARS
        else -> null
    }
}
