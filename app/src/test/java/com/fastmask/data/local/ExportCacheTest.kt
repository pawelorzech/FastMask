package com.fastmask.data.local

import android.content.Context
import com.fastmask.testutil.mask
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * The exported CSV is the only place in the app where every mask is written to
 * disk in plaintext, so its lifetime rules are worth pinning down. Everything
 * goes through the public surface: [ExportCache.export], [ExportCache.clear]
 * and [ExportCache.pruneExpired], asserting on the files that end up on disk.
 */
class ExportCacheTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun cache(): Pair<ExportCache, File> {
        val cacheDir = temp.newFolder("cache")
        val context = mockk<Context>()
        every { context.cacheDir } returns cacheDir
        return ExportCache(context) to File(cacheDir, "exports")
    }

    private suspend fun ExportCache.exportMasks(vararg ids: String): ExportOutcome =
        export(Dispatchers.Unconfined) { Result.success(ids.map { mask(it) }) }

    @Test
    fun `export writes a timestamped csv with the fetched masks`() = runBlocking {
        val (cache, dir) = cache()

        val outcome = cache.exportMasks("a")

        val file = (outcome as ExportOutcome.Written).file
        assertEquals(dir, file.parentFile)
        assertTrue(file.name, Regex("""fastmask-masks-\d+\.csv""").matches(file.name))
        assertTrue(file.readText().startsWith("email"))
        assertTrue(file.readText().contains("a@fastmail.com"))
    }

    // Ageing out rather than wiping: a share target may still be reading a URI
    // it was granted (a slow upload), so only clearly-stale files go.
    @Test
    fun `export removes exports older than an hour and keeps recent ones`() = runBlocking {
        val (cache, dir) = cache()
        val now = System.currentTimeMillis()
        dir.mkdirs()
        val stale = File(dir, "old.csv").apply {
            writeText("x")
            setLastModified(now - TimeUnit.HOURS.toMillis(2))
        }
        val recent = File(dir, "recent.csv").apply {
            writeText("x")
            setLastModified(now - TimeUnit.MINUTES.toMillis(5))
        }

        cache.exportMasks("a")

        assertTrue("a two-hour-old export should be gone", !stale.exists())
        assertTrue("a five-minute-old export may still be in use", recent.exists())
    }

    /*
     * Audit 2026-07-27. Ageing used to happen only inside the export, so the
     * documented "one hour" retention elapsed only on the NEXT export. A user
     * who exports once — the common case — left a plaintext CSV of every mask
     * they own sitting in the cache directory indefinitely, while every other
     * on-disk copy of that data is Keystore-encrypted. pruneExpired() is now
     * callable on its own and runs at every cold start.
     */
    @Test
    fun `pruneExpired ages out old exports without writing a new one`() {
        val (cache, dir) = cache()
        val now = TimeUnit.DAYS.toMillis(10)
        dir.mkdirs()
        val stale = File(dir, "old.csv").apply {
            writeText("x")
            setLastModified(now - TimeUnit.HOURS.toMillis(2))
        }
        val recent = File(dir, "recent.csv").apply {
            writeText("x")
            setLastModified(now - TimeUnit.MINUTES.toMillis(5))
        }

        cache.pruneExpired(now = now)

        assertTrue("a two-hour-old export should be gone", !stale.exists())
        assertTrue("a five-minute-old export may still be in use", recent.exists())
        assertEquals("pruning must not create an export", 1, dir.listFiles()!!.size)
    }

    // Signing out is the point at which the account's data should stop being on
    // the device — an export written minutes earlier must not outlive it.
    @Test
    fun `clear removes every export regardless of age`() = runBlocking {
        val (cache, dir) = cache()
        cache.exportMasks("a")
        File(dir, "earlier.csv").writeText("x")
        assertEquals(2, dir.listFiles()?.size)

        cache.clear()

        assertEquals(0, dir.listFiles()?.size ?: 0)
    }

    @Test
    fun `clear on a directory that was never created is a no-op`() {
        val (cache, dir) = cache()
        assertTrue(!dir.exists())

        cache.clear()
    }

    // An export is built from a network fetch, so "tap Export on a slow link,
    // then Log out from the same screen before it lands" used to write the
    // account's whole mask list — in plaintext — into the cache directory AFTER
    // the sign-out that was supposed to erase it. Ageing it out an hour later is
    // the wrong remedy for a file that should never have existed.
    @Test
    fun `a sign-out during the fetch abandons the write`() = runBlocking {
        val (cache, dir) = cache()

        val outcome = cache.export(Dispatchers.Unconfined) {
            cache.clear() // the sign-out lands while the fetch is still in flight
            Result.success(listOf(mask("a")))
        }

        assertEquals(ExportOutcome.WriteFailed, outcome)
        assertEquals(0, dir.listFiles()?.size ?: 0)
    }

    @Test
    fun `an export that started after the sign-out is written normally`() = runBlocking {
        val (cache, dir) = cache()
        cache.clear()

        // This export's fetch began after the sign-out, so it belongs to the new session.
        val outcome = cache.exportMasks("a")

        assertTrue(outcome is ExportOutcome.Written)
        assertEquals(1, dir.listFiles()?.size)
    }

    @Test
    fun `export reports a failed fetch without touching the disk`() = runBlocking {
        val (cache, dir) = cache()
        val boom = RuntimeException("boom")

        val outcome = cache.export(Dispatchers.Unconfined) { Result.failure(boom) }

        assertEquals(ExportOutcome.FetchFailed(boom), outcome)
        assertEquals(0, dir.listFiles()?.size ?: 0)
    }

    @Test
    fun `clear cannot finish between generation check and file write`() {
        val (cache, dir) = cache()
        val executor = Executors.newFixedThreadPool(2)
        val writeReachedDiskBoundary = CountDownLatch(1)
        val releaseWrite = CountDownLatch(1)
        val clearThread = AtomicReference<Thread>()
        cache.fileWriter = { file, text ->
            writeReachedDiskBoundary.countDown()
            check(releaseWrite.await(5, TimeUnit.SECONDS))
            file.writeText(text)
        }

        try {
            val export = executor.submit<ExportOutcome> { runBlocking { cache.exportMasks("a") } }
            assertTrue(writeReachedDiskBoundary.await(5, TimeUnit.SECONDS))

            val clear = executor.submit {
                clearThread.set(Thread.currentThread())
                cache.clear()
            }
            // Deterministic instead of a negative timed wait: a thread contending for a monitor
            // reports BLOCKED. If clear() did not need the lock the write holds, the thread would
            // finish instead and never be seen blocked.
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (clearThread.get()?.state != Thread.State.BLOCKED && !clear.isDone && System.nanoTime() < deadline) {
                Thread.onSpinWait()
            }
            assertEquals(
                "clear must wait until the in-flight file write leaves the critical section",
                Thread.State.BLOCKED,
                clearThread.get()?.state,
            )

            releaseWrite.countDown()
            assertTrue(export.get(5, TimeUnit.SECONDS) is ExportOutcome.Written)
            clear.get(5, TimeUnit.SECONDS)
            assertEquals("clear must leave no plaintext export behind", 0, dir.listFiles()?.size ?: 0)
        } finally {
            releaseWrite.countDown()
            executor.shutdownNow()
        }
    }
}
