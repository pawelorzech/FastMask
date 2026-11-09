package com.fastmask.ui.common

/*
 * Whatever is sent to the account is trimmed: the stray space an IME autocomplete
 * leaves must never reach it.
 */

internal fun String.trimToNull(): String? = trim().takeIf { it.isNotEmpty() }

/** An emptied field returns `""`, which clears the value on the server; null leaves it unchanged. */
internal fun String.trimmedIfChangedFrom(original: String?): String? =
    trim().takeIf { it != original.orEmpty() }
