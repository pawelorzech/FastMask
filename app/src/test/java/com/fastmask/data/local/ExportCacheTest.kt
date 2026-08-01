package com.fastmask.data.local

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * The exported CSV is the only place in the app where every mask is written to
 * disk in plaintext, so its lifetime rules are worth pinning down.
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

    @Test
    fun `write creates a timestamped file with the csv content`() {
        val (cache, dir) = cache()

        val file = cache.write("email,state\na@b.c,enabled\n", now = 1_000_000L)

        assertEquals("fastmask-masks-1000000.csv", file.name)
        assertEquals(dir, file.parentFile)
        assertTrue(file.readText().contains("a@b.c"))
    }

    // Ageing out rather than wiping: a share target may still be reading a URI
    // it was granted (a slow upload), so only clearly-stale files go.
    @Test
    fun `write removes exports older than an hour and keeps recent ones`() {
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

        cache.write("new", now = now)

        assertTrue("a two-hour-old export should be gone", !stale.exists())
        assertTrue("a five-minute-old export may still be in use", recent.exists())
    }

    /*
     * Audit 2026-07-27. Ageing used to happen only inside write(), so the
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
    fun `clear removes every export regardless of age`() {
        val (cache, dir) = cache()
        val now = TimeUnit.DAYS.toMillis(10)
        cache.write("first", now = now)
        cache.write("second", now = now + 1)
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
    fun `an export whose fetch outlived a sign-out is never written`() {
        val (cache, dir) = cache()
        val generation = cache.currentGeneration()

        cache.clear() // the sign-out lands while the fetch is still in flight

        val failure = runCatching { cache.write("every,mask", generation = generation) }
        assertTrue(failure.isFailure)
        assertEquals(0, dir.listFiles()?.size ?: 0)
    }

    @Test
    fun `an export that started after the sign-out is written normally`() {
        val (cache, dir) = cache()
        cache.clear()

        // Fresh generation read: this export belongs to the new session.
        cache.write("email,state\n", generation = cache.currentGeneration())

        assertEquals(1, dir.listFiles()?.size)
    }
}
