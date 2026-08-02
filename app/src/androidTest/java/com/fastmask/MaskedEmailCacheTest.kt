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

    /** Stand-ins for two accounts' owner markers. */
    private val owner = "owner-a"
    private val otherOwner = "owner-b"


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

        cache.write(masks, owner = owner, now = takenAt, generation = cache.currentGeneration())
        val restored = cache.read(owner)

        assertEquals(takenAt, restored?.cachedAt)
        assertEquals(masks, restored?.masks)
    }

    @Test
    fun readWithoutAWrittenCacheIsNull() {
        assertNull(cache.read(owner))
    }

    /** The mask list must not be readable by anything that gets the file. */
    @Test
    fun theFileOnDiskDoesNotContainTheAddressesInPlaintext() {
        cache.write(listOf(mask("secret")), owner = owner, now = takenAt, generation = cache.currentGeneration())

        val raw = cacheFile.readBytes().toString(Charsets.ISO_8859_1)

        assertTrue("cache file should exist", cacheFile.exists())
        assertEquals(-1, raw.indexOf("secret@fastmail.com"))
        assertEquals(-1, raw.indexOf("note for secret"))
    }

    /** Derived data: a damaged cache degrades to "no cache", never a crash. */
    @Test
    fun aCorruptCacheReadsAsNull() {
        cache.write(listOf(mask("one")), owner = owner, now = takenAt, generation = cache.currentGeneration())
        cacheFile.writeBytes(ByteArray(64) { 0x7A })

        assertNull(cache.read(owner))
    }

    @Test
    fun writingTwiceReplacesTheSnapshotRatherThanAppending() {
        cache.write(listOf(mask("first")), owner = owner, now = takenAt, generation = cache.currentGeneration())
        val later = takenAt.plusSeconds(3600)

        cache.write(listOf(mask("second")), owner = owner, now = later, generation = cache.currentGeneration())
        val restored = cache.read(owner)

        assertEquals(listOf("second"), restored?.masks?.map { it.id })
        assertEquals(later, restored?.cachedAt)
        assertNotEquals(takenAt, restored?.cachedAt)
    }

    @Test
    fun clearRemovesTheSnapshot() {
        cache.write(listOf(mask("one")), owner = owner, now = takenAt, generation = cache.currentGeneration())

        cache.clear()

        assertNull(cache.read(owner))
        assertTrue("file should be gone after clear", !cacheFile.exists())
    }

    /*
     * Audit 2026-08-01. The repository read the token, fetched over the network,
     * and wrote through on success with no re-check that the session still
     * existed. Pull-to-refresh on a slow link, then Settings -> Log out before
     * the response landed, and the in-flight continuation re-encrypted the whole
     * mask list back onto disk AFTER logout() had deleted it, where it stayed
     * until the next sign-in. docs/privacy.md promises the offline snapshot is
     * removed at log-out; it was not. Cancellation could not close this: there
     * is no suspension point between the response and the write.
     */
    @Test
    fun aWriteFromASessionThatHasSinceEndedIsDropped() {
        val generation = cache.currentGeneration()

        cache.clear() // the sign-out lands while the fetch is still in flight

        cache.write(listOf(mask("after-logout")), owner = owner, now = takenAt, generation = generation)

        assertNull("a superseded write must not resurrect the snapshot", cache.read(owner))
        assertTrue("file should not exist after a dropped write", !cacheFile.exists())
    }

    @Test
    fun aWriteFromTheCurrentSessionStillLands() {
        cache.clear()

        // A fetch started after the sign-out belongs to the new session.
        cache.write(listOf(mask("one")), owner = owner, now = takenAt, generation = cache.currentGeneration())

        assertEquals(listOf("one"), cache.read(owner)?.masks?.map { it.id })
    }

    /*
     * Audit 2026-07-27. The snapshot carried no owner and lives under a fixed
     * file name, so the only thing keeping one account's masks away from the
     * next account signed in on the device was clear() having succeeded — an
     * unchecked File.delete(). Now a snapshot answers only to the account that
     * wrote it.
     */

    @Test
    fun aSnapshotWrittenByOneAccountIsInvisibleToAnother() {
        cache.write(listOf(mask("account-a-mask")), owner = owner, now = takenAt, generation = cache.currentGeneration())

        assertNull(cache.read(otherOwner))
    }

    @Test
    fun aSnapshotFromBeforeOwnersExistedIsTreatedAsAbsent() {
        // A null owner is what an upgrade from the previous build reads back.
        cache.write(listOf(mask("legacy")), owner = null, now = takenAt, generation = cache.currentGeneration())

        assertNull(cache.read(owner))
    }

    /** An interrupted write must leave the previous good snapshot in place. */
    @Test
    fun aStrandedTempFileDoesNotDestroyTheLiveSnapshot() {
        cache.write(listOf(mask("good")), owner = owner, now = takenAt, generation = cache.currentGeneration())
        File(context.filesDir, "cache_staging").apply { mkdirs() }
            .resolve("masked_emails_cache.bin").writeBytes(ByteArray(32) { 0x5A })

        assertEquals(listOf("good"), cache.read(owner)?.masks?.map { it.id })
    }
}
