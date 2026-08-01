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
     * Bumped by every [clear], mirroring `MaskedEmailCache`. An export is built
     * from a network fetch, so the sequence "tap Export on a slow link, then Log
     * out from the same screen before it lands" wrote the account's complete
     * mask list — in plaintext, the one place in the app where that is true —
     * into the cache directory *after* the sign-out that was supposed to erase
     * it. Ageing out after an hour is the wrong answer there; the file should
     * never have been created.
     */
    private val generation = java.util.concurrent.atomic.AtomicLong(0)

    /** Snapshot of the current generation, for a caller about to fetch. */
    fun currentGeneration(): Long = generation.get()

    /**
     * Writes [csv] to a fresh timestamped file and returns it, after ageing out
     * exports old enough that no share can still be reading them.
     *
     * @param now injected so the ageing rule is testable without a clock.
     * @param generation what [currentGeneration] returned when the export's
     *   fetch began; a [clear] since then aborts the write.
     * @throws IllegalStateException when the session that asked for this export
     *   has ended. The caller already treats a failure as "export failed", which
     *   is the correct outcome: there is no session to export for any more.
     */
    fun write(
        csv: String,
        now: Long = System.currentTimeMillis(),
        generation: Long = this.generation.get(),
    ): File {
        check(generation == this.generation.get()) {
            "Export abandoned: the session ended before the file was written"
        }
        val dir = dir.apply { mkdirs() }
        pruneExpired(now)
        return File(dir, "fastmask-masks-$now.csv").apply { writeText(csv) }
    }

    /**
     * Drops exports past [MAX_AGE_MS].
     *
     * Extracted from [write] and called independently, because as a side effect
     * of writing it never ran for the user who exports once: the "one hour"
     * retention this class documents only elapsed on the NEXT export, so a
     * single CSV holding every mask in plaintext sat in the cache directory
     * indefinitely. Every other copy of that data on the device — the token,
     * the offline snapshot — is Keystore-encrypted; this one is not, so its
     * lifetime is the only thing protecting it.
     */
    fun pruneExpired(now: Long = System.currentTimeMillis()) {
        val cutoff = now - MAX_AGE_MS
        dir.listFiles()?.forEach { if (it.lastModified() < cutoff) it.delete() }
    }

    /** Drops every export regardless of age. Called on sign-out. */
    fun clear() {
        generation.incrementAndGet()
        dir.listFiles()?.forEach { it.delete() }
    }

    private companion object {
        val MAX_AGE_MS = TimeUnit.HOURS.toMillis(1)
        const val EXPORTS_DIR = "exports"
    }
}
