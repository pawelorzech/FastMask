package com.fastmask.data.local

import com.fastmask.domain.model.EmailState
import com.fastmask.domain.model.MaskedEmail
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The offline cache's plaintext format. Existing installs already hold a file
 * in this layout, so these tests pin it: a literal copy of what earlier builds
 * wrote must keep decoding, and today's encoder must keep producing it.
 */
class MaskedEmailSnapshotCodecTest {

    // Verbatim shape written by the pre-refactor build (upper-case state name,
    // epoch-millis instants, null fields omitted).
    private val legacyFile = """
        {"cachedAtEpochMs":1784883600000,"owner":"abc123","masks":[
          {"id":"m1","email":"a@fastmail.com","state":"ENABLED","forDomain":"https://example.com",
           "description":"Shop","createdAtEpochMs":1782892800000,"lastMessageAtEpochMs":1784534400000},
          {"id":"m2","email":"b@fastmail.com","state":"DELETED"},
          {"id":"m3","email":"c@fastmail.com","state":"SOMETHING_FROM_THE_FUTURE"}
        ]}
    """.trimIndent()

    @Test
    fun `a file written by an earlier build still decodes`() {
        val snapshot = MaskedEmailSnapshotCodec.decode(legacyFile)

        assertEquals("abc123", snapshot.owner)
        assertEquals(Instant.ofEpochMilli(1784883600000), snapshot.masks.cachedAt)
        val (m1, m2, m3) = snapshot.masks.masks
        assertEquals(EmailState.ENABLED, m1.state)
        assertEquals("https://example.com", m1.forDomain)
        assertEquals(Instant.ofEpochMilli(1782892800000), m1.createdAt)
        assertEquals(Instant.ofEpochMilli(1784534400000), m1.lastMessageAt)
        assertEquals(EmailState.DELETED, m2.state)
        assertNull(m2.description)
        assertNull(m2.createdAt)
        // Unknown state degrades to DISABLED on disk, without losing the row.
        assertEquals(EmailState.DISABLED, m3.state)
    }

    @Test
    fun `a file from before owners existed decodes with a null owner`() {
        val snapshot = MaskedEmailSnapshotCodec.decode(
            """{"cachedAtEpochMs":1,"masks":[]}""",
        )

        assertNull(snapshot.owner)
        assertEquals(emptyList<MaskedEmail>(), snapshot.masks.masks)
    }

    @Test
    fun `the encoder keeps the legacy layout`() {
        val mask = MaskedEmail(
            id = "m1", email = "a@fastmail.com", state = EmailState.PENDING,
            forDomain = null, description = "x", createdBy = null, url = null,
            emailPrefix = null, createdAt = Instant.ofEpochMilli(5), lastMessageAt = null,
        )

        val text = MaskedEmailSnapshotCodec.encode(
            MaskedEmailSnapshotCodec.payload(listOf(mask), "o", Instant.ofEpochMilli(9)),
        )

        assertEquals(
            """{"cachedAtEpochMs":9,"owner":"o","masks":[{"id":"m1","email":"a@fastmail.com",""" +
                """"state":"PENDING","description":"x","createdAtEpochMs":5}]}""",
            text,
        )
        assertEquals(listOf(mask), MaskedEmailSnapshotCodec.decode(text).masks.masks)
    }
}
