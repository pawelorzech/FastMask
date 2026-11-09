package com.fastmask.core

import java.security.MessageDigest

/** Changing the output would orphan every value already on disk. */
fun String.sha256Hex(): String =
    MessageDigest.getInstance("SHA-256")
        .digest(toByteArray())
        .joinToString("") { "%02x".format(it) }
