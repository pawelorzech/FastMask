package com.fastmask.data.local

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.VisibleForTesting
import androidx.core.content.FileProvider
import com.fastmask.core.IoDispatcher
import com.fastmask.domain.model.MaskCsv
import com.fastmask.domain.model.MaskedEmail
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
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
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    private val dir: File get() = File(context.cacheDir, EXPORTS_DIR)
    private val lock = Any()

    @VisibleForTesting
    internal var fileWriter: (File, String) -> Unit = { file, text -> file.writeText(text) }

    private val guard = GenerationGuard()

    suspend fun export(fetch: suspend () -> Result<List<MaskedEmail>>): ExportOutcome {
        val generation = guard.current()
        val csv = fetch().mapCatching { masks -> MaskCsv.build(masks) }
            .getOrElse { return ExportOutcome.FetchFailed(it) }
        return withContext(ioDispatcher) { runCatching { write(csv, generation) } }
            .fold(
                onSuccess = { ExportOutcome.Written(it) },
                onFailure = { ExportOutcome.WriteFailed },
            )
    }

    // The read grant on the FileProvider URI means the share target needs no
    // storage permission.
    fun shareIntent(file: File): Intent {
        val uri: Uri = FileProvider.getUriForFile(context, context.packageName + AUTHORITY_SUFFIX, file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun write(csv: String, generation: Long): File = synchronized(lock) {
        check(guard.isCurrent(generation)) {
            "Export abandoned: the session ended before the file was written"
        }
        val now = System.currentTimeMillis()
        val dir = dir.apply { mkdirs() }
        pruneExpired(now)
        File(dir, "fastmask-masks-$now.csv").also { fileWriter(it, csv) }
    }

    fun pruneExpired(now: Long = System.currentTimeMillis()) = synchronized(lock) {
        val cutoff = now - MAX_AGE_MS
        dir.listFiles()?.forEach { if (it.lastModified() < cutoff) it.delete() }
    }

    fun clear() = synchronized(lock) {
        guard.advance()
        dir.listFiles()?.forEach { it.delete() }
    }

    companion object {
        private val MAX_AGE_MS = TimeUnit.HOURS.toMillis(1)

        /** Also the `cache-path` in `res/xml/file_paths.xml`. */
        private const val EXPORTS_DIR = "exports"

        /**
         * Appended to the application id to form the FileProvider authority; the
         * manifest's `${applicationId}.fileprovider` must stay equal to it.
         */
        private const val AUTHORITY_SUFFIX = ".fileprovider"
    }
}

sealed interface ExportOutcome {
    data class Written(val file: File) : ExportOutcome
    data class FetchFailed(val error: Throwable) : ExportOutcome
    data object WriteFailed : ExportOutcome
}
