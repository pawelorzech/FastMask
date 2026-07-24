package com.fastmask

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fastmask.data.local.MaskedEmailCache
import com.fastmask.domain.model.EmailState
import com.fastmask.domain.model.MaskedEmail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant

/**
 * Instrumented because the cache is encrypted with a Keystore-backed key —
 * there is no Keystore on the JVM, and testing the plaintext path instead
 * would test something the app never runs.
 */
@RunWith(AndroidJUnit4::class)
class MaskedEmailCacheTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val cache = MaskedEmailCache(context)
    private val cacheFile = File(context.filesDir, "masked_emails_cache.bin")

    private val takenAt: Instant = Instant.parse("2026-07-24T09:00:00Z")

    private fun mask(id: String, state: EmailState = EmailState.ENABLED) = MaskedEmail(
        id = id,
        email = "$id@fastmail.com",
        state = state,
        forDomain = "example.com",
        description = "note for $id",
        createdBy = "test",
        url = "https://example.com",
        emailPrefix = id,
        createdAt = Instant.parse("2026-07-01T08:00:00Z"),
        lastMessageAt = Instant.parse("2026-07-20T08:00:00Z"),
    )

    @Before
    fun clean() {
        cache.clear()
    }

    @Test
    fun writeThenReadRoundTripsEveryFieldAndTheTimestamp() {
        val masks = listOf(mask("one"), mask("two", EmailState.DISABLED))

        cache.write(masks, now = takenAt)
        val restored = cache.read()

        assertEquals(takenAt, restored?.cachedAt)
        assertEquals(masks, restored?.masks)
    }

    @Test
    fun readWithoutAWrittenCacheIsNull() {
        assertNull(cache.read())
    }

    /** The mask list must not be readable by anything that gets the file. */
    @Test
    fun theFileOnDiskDoesNotContainTheAddressesInPlaintext() {
        cache.write(listOf(mask("secret")), now = takenAt)

        val raw = cacheFile.readBytes().toString(Charsets.ISO_8859_1)

        assertTrue("cache file should exist", cacheFile.exists())
        assertEquals(-1, raw.indexOf("secret@fastmail.com"))
        assertEquals(-1, raw.indexOf("note for secret"))
    }

    /** Derived data: a damaged cache degrades to "no cache", never a crash. */
    @Test
    fun aCorruptCacheReadsAsNull() {
        cache.write(listOf(mask("one")), now = takenAt)
        cacheFile.writeBytes(ByteArray(64) { 0x7A })

        assertNull(cache.read())
    }

    @Test
    fun writingTwiceReplacesTheSnapshotRatherThanAppending() {
        cache.write(listOf(mask("first")), now = takenAt)
        val later = takenAt.plusSeconds(3600)

        cache.write(listOf(mask("second")), now = later)
        val restored = cache.read()

        assertEquals(listOf("second"), restored?.masks?.map { it.id })
        assertEquals(later, restored?.cachedAt)
        assertNotEquals(takenAt, restored?.cachedAt)
    }

    @Test
    fun clearRemovesTheSnapshot() {
        cache.write(listOf(mask("one")), now = takenAt)

        cache.clear()

        assertNull(cache.read())
        assertTrue("file should be gone after clear", !cacheFile.exists())
    }
}
