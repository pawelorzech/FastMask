package com.fastmask.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns `cacheDir/exports`, where the CSV export (Pro) is written before being
 * handed to the share sheet through a FileProvider URI.
 *
 * The directory holds every mask in plaintext — the single place in the app
 * where that is true — so its lifetime matters. Two rules:
 *
 * - **Age out, don't wipe.** A share target may still be reading a URI it was
 *   granted (a slow upload to Drive), so only files older than [MAX_AGE_MS] are
 *   removed. Timestamped names keep each share's content stable meanwhile.
 * - **Clear on sign-out.** Ageing out is not enough there: signing out is the
 *   point at which the account's data should stop being on the device, and an
 *   export written minutes earlier would otherwise outlive the session.
 */
@Singleton
class ExportCache @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val dir: File get() = File(context.cacheDir, EXPORTS_DIR)

    /**
     * Writes [csv] to a fresh timestamped file and returns it, after ageing out
     * exports old enough that no share can still be reading them.
     *
     * @param now injected so the ageing rule is testable without a clock.
     */
    fun write(csv: String, now: Long = System.currentTimeMillis()): File {
        val dir = dir.apply { mkdirs() }
        val cutoff = now - MAX_AGE_MS
        dir.listFiles()?.forEach { if (it.lastModified() < cutoff) it.delete() }
        return File(dir, "fastmask-masks-$now.csv").apply { writeText(csv) }
    }

    /** Drops every export regardless of age. Called on sign-out. */
    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    private companion object {
        val MAX_AGE_MS = TimeUnit.HOURS.toMillis(1)
        const val EXPORTS_DIR = "exports"
    }
}
