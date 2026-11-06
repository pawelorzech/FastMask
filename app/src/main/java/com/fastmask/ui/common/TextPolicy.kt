package com.fastmask.ui.common

/*
 * The text policy of the mask form, shared by the create and detail
 * ViewModels: whatever is sent to the account is trimmed, because the stray
 * space an IME autocomplete leaves must never reach it.
 */

/** Create: a blank field means "not set". */
internal fun String.trimToNull(): String? = trim().takeIf { it.isNotEmpty() }

/**
 * Detail: the trimmed text when it differs from the stored [original], otherwise
 * null ("leave unchanged"). An emptied field comes back as `""`, which clears
 * the value on the server, so null and blank are different answers here.
 */
internal fun String.trimmedIfChangedFrom(original: String?): String? =
    trim().takeIf { it != original.orEmpty() }
