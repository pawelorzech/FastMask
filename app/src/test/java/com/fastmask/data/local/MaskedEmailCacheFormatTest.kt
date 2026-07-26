package com.fastmask.data.local

import com.fastmask.domain.hygiene.HygieneBaseline
import com.fastmask.domain.model.EmailState
import com.fastmask.domain.model.MaskedEmail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Instant

/**
 * The on-disk FORMAT of the mask cache, now that it also carries the hygiene
 * review's baseline.
 *
 * This is an app that is already installed on phones. Every file the previous
 * version wrote has no `hygieneBaseline` key at all, and the update must read
 * those files without a murmur — a crash after an update is not a bug the user
 * can work around, they simply lose the app. So every degenerate shape the
 * field can take is pinned here: absent, empty, damaged, written by a version
 * that does not exist yet, unreadable, unwritable.
 *
 * The one invariant behind all of it: **nothing about the baseline may ever
 * cost the user their cached mask list, and nothing may throw.** The baseline
 * is a convenience; the mask list is what makes the app work offline.
 *
 * Runs on the JVM because [MaskedEmailCache] takes its bytes from a
 * [SnapshotStore]. The shipping store encrypts with a Keystore-backed key,
 * which does not exist here — the encryption itself is covered by the
 * instrumented `MaskedEmailCacheTest`.
 */
class MaskedEmailCacheFormatTest {

    private val takenAt: Instant = Instant.parse("2026-07-24T09:00:00Z")
    private val reviewedAt: Instant = Instant.parse("2026-07-25T10:00:00Z")

    /** In-memory bytes, with the two failure modes the real store has. */
    private class FakeSnapshotStore(
        var bytes: ByteArray? = null,
        var readFailure: Throwable? = null,
        var writeFailure: Throwable? = null,
    ) : SnapshotStore {
        var writes = 0

        override fun read(): ByteArray? {
            readFailure?.let { throw it }
            return bytes
        }

        override fun write(bytes: ByteArray) {
            writes++
            writeFailure?.let { throw it }
            this.bytes = bytes
        }

        override fun clear() {
            bytes = null
        }

        fun text(): String? = bytes?.toString(Charsets.UTF_8)

        fun put(json: String) {
            bytes = json.toByteArray()
        }
    }

    private fun mask(
        id: String,
        state: EmailState = EmailState.ENABLED,
        lastMessageAt: Instant? = Instant.parse("2026-07-20T08:00:00Z"),
    ) = MaskedEmail(
        id = id,
        email = "$id@fastmail.com",
        state = state,
        forDomain = "example.com",
        description = "note for $id",
        createdBy = "test",
        url = "https://example.com",
        emailPrefix = id,
        createdAt = Instant.parse("2026-07-01T08:00:00Z"),
        lastMessageAt = lastMessageAt,
    )

    /** Exactly what the shipped version writes today: no baseline key at all. */
    private fun legacySnapshotJson(): String = """
        {
          "cachedAtEpochMs": ${takenAt.toEpochMilli()},
          "masks": [
            {
              "id": "one",
              "email": "one@fastmail.com",
              "state": "ENABLED",
              "forDomain": "example.com",
              "description": "note for one",
              "createdBy": "test",
              "url": "https://example.com",
              "emailPrefix": "one",
              "createdAtEpochMs": 1751356800000,
              "lastMessageAtEpochMs": 1753000000000
            }
          ]
        }
    """.trimIndent()

    private fun cacheWith(store: FakeSnapshotStore) = MaskedEmailCache(store) to store

    // --- existing installs: the file that predates the field ---------------

    @Test
    fun `a snapshot written before the field existed still reads its masks`() {
        val store = FakeSnapshotStore()
        store.put(legacySnapshotJson())
        val (cache, _) = cacheWith(store)

        val restored = cache.read()

        assertNotNull("an old cache file must survive the update", restored)
        assertEquals(listOf("one"), restored?.masks?.map { it.id })
        assertEquals(takenAt, restored?.cachedAt)
    }

    @Test
    fun `a snapshot written before the field existed has no baseline`() {
        val store = FakeSnapshotStore()
        store.put(legacySnapshotJson())
        val (cache, _) = cacheWith(store)

        assertNull(cache.readHygieneBaseline())
    }

    /** The first review on an existing install: adding the field keeps the masks. */
    @Test
    fun `writing a baseline onto a legacy snapshot preserves the mask list`() {
        val store = FakeSnapshotStore()
        store.put(legacySnapshotJson())
        val (cache, _) = cacheWith(store)

        cache.writeHygieneBaseline(
            HygieneBaseline(reviewedAt, mapOf("one" to Instant.ofEpochMilli(1753000000000L))),
        )

        assertEquals(listOf("one"), cache.read()?.masks?.map { it.id })
        assertEquals(takenAt, cache.read()?.cachedAt)
        assertEquals(reviewedAt, cache.readHygieneBaseline()?.reviewedAt)
    }

    // --- the retention promise ---------------------------------------------

    /**
     * The whole reason the baseline lives here rather than in the mask list:
     * `getMaskedEmails()` write-throughs the cache on every success, and the
     * list screen fires one on every RESUME. If those writes dropped the
     * baseline, the "new activity" category would be dead on arrival.
     */
    @Test
    fun `a mask write-through carries the existing baseline forward`() {
        val store = FakeSnapshotStore()
        val (cache, _) = cacheWith(store)
        cache.write(listOf(mask("one")), now = takenAt)
        val baseline = HygieneBaseline(reviewedAt, mapOf("one" to null))
        cache.writeHygieneBaseline(baseline)

        repeat(5) { index ->
            cache.write(listOf(mask("one"), mask("two")), now = takenAt.plusSeconds(index + 1L))
        }

        val restored = cache.readHygieneBaseline()
        assertEquals(reviewedAt, restored?.reviewedAt)
        assertEquals(mapOf("one" to null), restored?.lastMessageAtById)
    }

    @Test
    fun `a baseline round trips every entry including the never used ones`() {
        val store = FakeSnapshotStore()
        val (cache, _) = cacheWith(store)
        cache.write(listOf(mask("one"), mask("two")), now = takenAt)
        val sent = Instant.parse("2026-07-22T11:00:00Z")

        cache.writeHygieneBaseline(
            HygieneBaseline(reviewedAt, mapOf("one" to sent, "two" to null)),
        )

        val restored = cache.readHygieneBaseline()
        assertEquals(reviewedAt, restored?.reviewedAt)
        assertEquals(mapOf("one" to sent, "two" to null), restored?.lastMessageAtById)
        // A present key with a null value is not the same fact as an absent
        // key, and the file has to keep them apart.
        assertTrue(restored!!.lastMessageAtById.containsKey("two"))
    }

    @Test
    fun `writing the baseline twice keeps the last one and the masks`() {
        val store = FakeSnapshotStore()
        val (cache, _) = cacheWith(store)
        cache.write(listOf(mask("one")), now = takenAt)

        cache.writeHygieneBaseline(HygieneBaseline(reviewedAt, mapOf("one" to null)))
        val later = reviewedAt.plusSeconds(3600)
        cache.writeHygieneBaseline(HygieneBaseline(later, mapOf("one" to takenAt, "two" to null)))

        val restored = cache.readHygieneBaseline()
        assertEquals(later, restored?.reviewedAt)
        assertEquals(mapOf("one" to takenAt, "two" to null), restored?.lastMessageAtById)
        assertEquals(listOf("one"), cache.read()?.masks?.map { it.id })
    }

    /**
     * A baseline is only meaningful next to a mask list. Writing a mask-less
     * snapshot just to hold one would make the offline list answer "cached: 0
     * masks" when the honest answer is "nothing cached at all".
     */
    @Test
    fun `a baseline written with no snapshot on disk is dropped rather than inventing one`() {
        val store = FakeSnapshotStore()
        val (cache, _) = cacheWith(store)

        cache.writeHygieneBaseline(HygieneBaseline(reviewedAt, mapOf("one" to null)))

        assertNull(cache.read())
        assertNull(cache.readHygieneBaseline())
        assertNull(store.bytes)
    }

    // --- damaged and unexpected shapes -------------------------------------

    @Test
    fun `an empty baseline object degrades instead of throwing`() {
        val store = FakeSnapshotStore()
        store.put("""{"cachedAtEpochMs": ${takenAt.toEpochMilli()}, "masks": [], "hygieneBaseline": {}}""")
        val (cache, _) = cacheWith(store)

        val baseline = cache.readHygieneBaseline()

        assertEquals(Instant.EPOCH, baseline?.reviewedAt)
        assertEquals(emptyMap<String, Instant?>(), baseline?.lastMessageAtById)
    }

    @Test
    fun `a null baseline field reads as no baseline`() {
        val store = FakeSnapshotStore()
        store.put("""{"cachedAtEpochMs": ${takenAt.toEpochMilli()}, "masks": [], "hygieneBaseline": null}""")
        val (cache, _) = cacheWith(store)

        assertNull(cache.readHygieneBaseline())
    }

    /** Every wrong-typed shape costs the category, never the mask list. */
    @Test
    fun `a baseline of the wrong shape costs the category and nothing else`() {
        val damaged = listOf(
            """ "not an object" """,
            """ 42 """,
            """ [1, 2, 3] """,
            """ {"reviewedAtEpochMs": "yesterday"} """,
            """ {"entries": "gone"} """,
            """ {"entries": [{"id": 7}]} """,
        )

        damaged.forEach { shape ->
            val store = FakeSnapshotStore()
            store.put(
                """
                {
                  "cachedAtEpochMs": ${takenAt.toEpochMilli()},
                  "masks": [{"id": "one", "email": "one@fastmail.com", "state": "ENABLED"}],
                  "hygieneBaseline": $shape
                }
                """.trimIndent(),
            )
            val (cache, _) = cacheWith(store)

            assertNull("baseline shape $shape should degrade to null", cache.readHygieneBaseline())
            assertEquals(
                "baseline shape $shape must not cost the mask list",
                listOf("one"),
                cache.read()?.masks?.map { it.id },
            )
        }
    }

    /**
     * A downgrade, or a file synced from a device on a newer build. Unknown
     * keys are ignored and what is understood is still used.
     */
    @Test
    fun `a baseline from a future version keeps the fields this version knows`() {
        val store = FakeSnapshotStore()
        store.put(
            """
            {
              "cachedAtEpochMs": ${takenAt.toEpochMilli()},
              "masks": [{"id": "one", "email": "one@fastmail.com", "state": "ENABLED"}],
              "hygieneBaseline": {
                "reviewedAtEpochMs": ${reviewedAt.toEpochMilli()},
                "entries": [
                  {"id": "one", "lastMessageAtEpochMs": 1753000000000, "sentimentScore": 0.5}
                ],
                "reviewedOnDevice": "pixel-9",
                "schemaVersion": 4
              }
            }
            """.trimIndent(),
        )
        val (cache, _) = cacheWith(store)

        val baseline = cache.readHygieneBaseline()

        assertEquals(reviewedAt, baseline?.reviewedAt)
        assertEquals(
            mapOf("one" to Instant.ofEpochMilli(1753000000000L)),
            baseline?.lastMessageAtById,
        )
        assertEquals(listOf("one"), cache.read()?.masks?.map { it.id })
    }

    /** A half-written entry loses that row, not the file. */
    @Test
    fun `entries with no id are dropped and the rest survive`() {
        val store = FakeSnapshotStore()
        store.put(
            """
            {
              "cachedAtEpochMs": ${takenAt.toEpochMilli()},
              "masks": [],
              "hygieneBaseline": {
                "reviewedAtEpochMs": ${reviewedAt.toEpochMilli()},
                "entries": [{}, {"id": "one", "lastMessageAtEpochMs": 1753000000000}]
              }
            }
            """.trimIndent(),
        )
        val (cache, _) = cacheWith(store)

        assertEquals(
            mapOf("one" to Instant.ofEpochMilli(1753000000000L)),
            cache.readHygieneBaseline()?.lastMessageAtById,
        )
    }

    @Test
    fun `a truncated file reads as nothing at all`() {
        val store = FakeSnapshotStore()
        store.put("""{"cachedAtEpochMs": 12345, "masks": [{"id": "one",""")
        val (cache, _) = cacheWith(store)

        assertNull(cache.read())
        assertNull(cache.readHygieneBaseline())
    }

    @Test
    fun `bytes that are not json at all read as nothing`() {
        val store = FakeSnapshotStore(bytes = ByteArray(64) { 0x7A })
        val (cache, _) = cacheWith(store)

        assertNull(cache.read())
        assertNull(cache.readHygieneBaseline())
    }

    // --- storage that will not cooperate ------------------------------------

    @Test
    fun `no file on disk is not an error`() {
        val store = FakeSnapshotStore()
        val (cache, _) = cacheWith(store)

        assertNull(cache.read())
        assertNull(cache.readHygieneBaseline())
    }

    /** A rotated Keystore key makes the file undecryptable. That is not a crash. */
    @Test
    fun `a decryption failure reads as nothing rather than throwing`() {
        val store = FakeSnapshotStore(readFailure = IOException("cannot decrypt"))
        val (cache, _) = cacheWith(store)

        assertNull(cache.read())
        assertNull(cache.readHygieneBaseline())
    }

    @Test
    fun `a decryption failure does not stop a fresh snapshot from being written`() {
        val store = FakeSnapshotStore(readFailure = IOException("cannot decrypt"))
        val (cache, _) = cacheWith(store)

        cache.write(listOf(mask("one")), now = takenAt)

        store.readFailure = null
        assertEquals(listOf("one"), cache.read()?.masks?.map { it.id })
    }

    @Test
    fun `a failed write leaves the previous file untouched`() {
        val store = FakeSnapshotStore()
        val (cache, _) = cacheWith(store)
        cache.write(listOf(mask("one")), now = takenAt)
        val before = store.text()

        store.writeFailure = IOException("no space left on device")
        cache.write(listOf(mask("two")), now = takenAt.plusSeconds(60))

        assertEquals(before, store.text())
        assertEquals(listOf("one"), cache.read()?.masks?.map { it.id })
    }

    @Test
    fun `a failed baseline write leaves the mask list readable`() {
        val store = FakeSnapshotStore()
        val (cache, _) = cacheWith(store)
        cache.write(listOf(mask("one")), now = takenAt)

        store.writeFailure = IOException("no space left on device")
        cache.writeHygieneBaseline(HygieneBaseline(reviewedAt, mapOf("one" to null)))

        assertEquals(listOf("one"), cache.read()?.masks?.map { it.id })
        assertNull(cache.readHygieneBaseline())
    }

    @Test
    fun `clear drops the masks and the baseline together`() {
        val store = FakeSnapshotStore()
        val (cache, _) = cacheWith(store)
        cache.write(listOf(mask("one")), now = takenAt)
        cache.writeHygieneBaseline(HygieneBaseline(reviewedAt, mapOf("one" to null)))

        cache.clear()

        assertNull(cache.read())
        assertNull(cache.readHygieneBaseline())
    }

    /** An unknown state name degrades that row; the file is still a cache. */
    @Test
    fun `an unknown mask state degrades to disabled rather than losing the file`() {
        val store = FakeSnapshotStore()
        store.put(
            """
            {
              "cachedAtEpochMs": ${takenAt.toEpochMilli()},
              "masks": [{"id": "one", "email": "one@fastmail.com", "state": "QUARANTINED"}]
            }
            """.trimIndent(),
        )
        val (cache, _) = cacheWith(store)

        assertEquals(listOf(EmailState.DISABLED), cache.read()?.masks?.map { it.state })
    }
}
