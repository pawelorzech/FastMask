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
}
