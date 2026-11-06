package com.fastmask.core

import java.security.MessageDigest

/**
 * Lower-case hex SHA-256 of the UTF-8 bytes. Used for opaque markers derived
 * from secrets (the offline-snapshot owner, the entitlement proof), so the
 * stored values are only ever comparable, never reversible. Changing the
 * output would orphan every value already on disk.
 */
fun String.sha256Hex(): String =
    MessageDigest.getInstance("SHA-256")
        .digest(toByteArray())
        .joinToString("") { "%02x".format(it) }
