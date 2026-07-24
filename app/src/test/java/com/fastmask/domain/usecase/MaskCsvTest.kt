package com.fastmask.domain.usecase

import com.fastmask.domain.model.EmailState
import com.fastmask.testutil.mask
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MaskCsvTest {

    @Test
    fun `header comes first and covers all exported fields`() {
        val csv = MaskCsv.build(emptyList())
        assertEquals(
            "email,state,for_domain,description,url,created_at,last_message_at",
            csv.lineSequence().first(),
        )
    }

    @Test
    fun `every mask is exported including archived ones`() {
        val csv = MaskCsv.build(
            listOf(
                mask("active", state = EmailState.ENABLED),
                mask("off", state = EmailState.DISABLED),
                mask("gone", state = EmailState.DELETED),
            )
        )
        val lines = csv.trim().lines()
        assertEquals(4, lines.size) // header + 3 masks
        assertTrue(lines[3].startsWith("gone@fastmail.com,deleted"))
    }

    @Test
    fun `fields with commas and quotes are RFC 4180 escaped`() {
        val csv = MaskCsv.build(
            listOf(mask("a", description = """shop, the "best" one"""))
        )
        assertTrue(csv.contains("\"shop, the \"\"best\"\" one\""))
    }

    @Test
    fun `formula-leading fields are neutralized against spreadsheet injection`() {
        val csv = MaskCsv.build(
            listOf(
                mask(
                    "a",
                    description = "=HYPERLINK(\"http://evil\")",
                    forDomain = "+spam.example",
                    url = "@import",
                )
            )
        )
        // Leading apostrophe forces text interpretation in Excel/Sheets.
        assertTrue(csv.contains("\"'=HYPERLINK(\"\"http://evil\"\")\""))
        assertTrue(csv.contains("'+spam.example"))
        assertTrue(csv.contains("'@import"))
    }

    @Test
    fun `control-character-leading fields are neutralized too`() {
        // OWASP: some spreadsheet parsers strip leading CR/LF/tab and then
        // evaluate the remainder as a formula — quoting alone is not enough.
        val csv = MaskCsv.build(
            listOf(
                mask(
                    "a",
                    description = "\r=HYPERLINK(\"http://evil\")",
                    forDomain = "\n=1+1",
                    url = "\t=cmd",
                )
            )
        )
        assertTrue(csv.contains("\"'\r=HYPERLINK(\"\"http://evil\"\")\""))
        assertTrue(csv.contains("\"'\n=1+1\""))
        assertTrue(csv.contains("'\t=cmd"))
    }

    @Test
    fun `leading-whitespace formula is neutralized (importers that trim first)`() {
        // A field starting with spaces before the formula lead must still be
        // neutralized: some importers strip leading whitespace before evaluating.
        val csv = MaskCsv.build(
            listOf(mask("a", description = "  =HYPERLINK(\"http://evil\")"))
        )
        // Apostrophe prepended even though the first char is a space.
        assertTrue(csv.contains("\"'  =HYPERLINK(\"\"http://evil\"\")\""))
    }

    @Test
    fun `benign leading-whitespace text is not altered`() {
        val csv = MaskCsv.build(listOf(mask("a", description = " just a note")))
        assertTrue(csv.contains(" just a note"))
        assertTrue(!csv.contains("' just a note"))
    }

    @Test
    fun `timestamps are exported as ISO instants`() {
        val csv = MaskCsv.build(
            listOf(mask("a", createdAt = Instant.parse("2026-01-02T03:04:05Z")))
        )
        assertTrue(csv.contains("2026-01-02T03:04:05Z"))
    }
}
