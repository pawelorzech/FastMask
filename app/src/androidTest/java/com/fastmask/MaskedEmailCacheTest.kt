package com.fastmask

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.security.crypto.EncryptedFile
import com.fastmask.data.local.MaskedEmailCache
import com.fastmask.data.local.appMasterKey
import kotlinx.coroutines.runBlocking
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

    private fun store(masks: List<MaskedEmail>, at: Instant = takenAt) {
        runBlocking { cache.refresh(owner, at) { Result.success(masks) } }
    }

    @Before
    fun clean() {
        cache.clear()
    }

    @Test
    fun writeThenReadRoundTripsEveryFieldAndTheTimestamp() {
        val masks = listOf(mask("one"), mask("two", EmailState.DISABLED))

        store(masks)
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
        store(listOf(mask("secret")))

        val raw = cacheFile.readBytes().toString(Charsets.ISO_8859_1)

        assertTrue("cache file should exist", cacheFile.exists())
        assertEquals(-1, raw.indexOf("secret@fastmail.com"))
        assertEquals(-1, raw.indexOf("note for secret"))
    }

    /** Derived data: a damaged cache degrades to "no cache", never a crash. */
    @Test
    fun aCorruptCacheReadsAsNull() {
        store(listOf(mask("one")))
        cacheFile.writeBytes(ByteArray(64) { 0x7A })

        assertNull(cache.read(owner))
    }

    @Test
    fun writingTwiceReplacesTheSnapshotRatherThanAppending() {
        store(listOf(mask("first")))
        val later = takenAt.plusSeconds(3600)

        store(listOf(mask("second")), at = later)
        val restored = cache.read(owner)

        assertEquals(listOf("second"), restored?.masks?.map { it.id })
        assertEquals(later, restored?.cachedAt)
        assertNotEquals(takenAt, restored?.cachedAt)
    }

    @Test
    fun clearRemovesTheSnapshot() {
        store(listOf(mask("one")))

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
    fun aFetchThatFinishesAfterSignOutLeavesNothingBehind() {
        val result = runBlocking {
            cache.refresh(owner, takenAt) {
                cache.clear() // the sign-out lands while the fetch is still in flight
                Result.success(listOf(mask("after-logout")))
            }
        }

        assertEquals("the caller still gets its answer", listOf("after-logout"), result.getOrThrow().map { it.id })
        assertNull("a superseded write must not resurrect the snapshot", cache.read(owner))
        assertTrue("file should not exist after a dropped write", !cacheFile.exists())
    }

    @Test
    fun aWriteFromTheCurrentSessionStillLands() {
        cache.clear()

        // A fetch started after the sign-out belongs to the new session.
        store(listOf(mask("one")))

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
        store(listOf(mask("account-a-mask")))

        assertNull(cache.read(otherOwner))
    }

    @Test
    fun aSnapshotFromBeforeOwnersExistedIsTreatedAsAbsent() {
        // What an upgrade from the previous build finds on disk: a snapshot with
        // no owner field, encrypted under the same key and file name.
        val legacy = """{"cachedAtEpochMs":${takenAt.toEpochMilli()},"masks":[{"id":"legacy","email":"legacy@fastmail.com","state":"ENABLED"}]}"""
        EncryptedFile.Builder(
            context,
            cacheFile,
            appMasterKey(context),
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB,
        ).build().openFileOutput().use { it.write(legacy.toByteArray()) }
        assertTrue("legacy file should be on disk", cacheFile.exists())

        assertNull(cache.read(owner))
    }

    @Test
    fun aFailedFetchLeavesTheSnapshotAlone() {
        store(listOf(mask("good")))

        val result = runBlocking { cache.refresh(owner, takenAt) { Result.failure(java.io.IOException("offline")) } }

        assertTrue(result.isFailure)
        assertEquals(listOf("good"), cache.read(owner)?.masks?.map { it.id })
    }

    /** An interrupted write must leave the previous good snapshot in place. */
    @Test
    fun aStrandedTempFileDoesNotDestroyTheLiveSnapshot() {
        store(listOf(mask("good")))
        File(context.filesDir, "cache_staging").apply { mkdirs() }
            .resolve("masked_emails_cache.bin").writeBytes(ByteArray(32) { 0x5A })

        assertEquals(listOf("good"), cache.read(owner)?.masks?.map { it.id })
    }
}
