package com.fastmask.domain.crash

interface CrashReporter {

    /** Turns collection on or off for the rest of the process and future launches. */
    fun setCollectionEnabled(enabled: Boolean)

    fun deleteUnsentReports()
}
