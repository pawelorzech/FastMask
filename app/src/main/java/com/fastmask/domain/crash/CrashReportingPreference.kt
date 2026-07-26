package com.fastmask.domain.crash

/**
 * What the stored opt-out preference says — including the case where it could
 * not be read at all.
 *
 * The distinction is the whole point of this type. "No value stored" and "the
 * store could not be read" both used to collapse into the same `true`, which
 * meant a transient DataStore failure re-enabled collection for someone who had
 * explicitly opted out. Absence of a key is a fresh or upgraded install and must
 * default to on; a failed read says nothing about what the user wanted, so it
 * must not be turned into a decision on their behalf.
 */
sealed interface CrashReportingPreference {

    /** The user made an explicit choice and it is on disk. */
    data class Stored(val enabled: Boolean) : CrashReportingPreference

    /** No value stored: a fresh install, or one updating into this version. */
    data object Missing : CrashReportingPreference

    /**
     * The preferences file could not be read, or holds a value of the wrong
     * type under this name. The user's last explicit choice is unknown.
     */
    data object Unreadable : CrashReportingPreference

    /**
     * The value to *display*. Never use this to drive the SDK — [Unreadable]
     * resolves to the default here, which is exactly the re-enabling that must
     * not happen. [CrashReportingStartup] handles that case separately.
     */
    val enabledOrDefault: Boolean
        get() = when (this) {
            is Stored -> enabled
            Missing, Unreadable -> CrashReportingPolicy.DEFAULT_ENABLED
        }
}
