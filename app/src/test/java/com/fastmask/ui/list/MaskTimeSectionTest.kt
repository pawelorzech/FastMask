package com.fastmask.ui.list

import com.fastmask.testutil.mask
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class MaskTimeSectionTest {

    private val now: Instant = Instant.parse("2026-06-15T12:00:00Z")
    private val nowSec = now.epochSecond

    private fun ago(seconds: Long): Instant = now.minusSeconds(seconds)

    private fun sectionOf(createdAt: Instant?, lastMessageAt: Instant? = null) =
        maskTimeSection(mask("m", createdAt = createdAt, lastMessageAt = lastMessageAt), nowSec)

    @Test
    fun `thresholds match the relative time label`() {
        assertEquals(MaskTimeSection.TODAY, sectionOf(ago(30)))
        assertEquals(MaskTimeSection.TODAY, sectionOf(ago(23 * 3_600L)))
        assertEquals(MaskTimeSection.THIS_WEEK, sectionOf(ago(24 * 3_600L)))
        assertEquals(MaskTimeSection.THIS_WEEK, sectionOf(ago(6 * 86_400L)))
        assertEquals(MaskTimeSection.THIS_MONTH, sectionOf(ago(7 * 86_400L)))
        assertEquals(MaskTimeSection.THIS_MONTH, sectionOf(ago(29 * 86_400L)))
        assertEquals(MaskTimeSection.EARLIER, sectionOf(ago(30 * 86_400L)))
    }

    @Test
    fun `last message beats creation time and a missing time is earlier`() {
        assertEquals(MaskTimeSection.TODAY, sectionOf(createdAt = ago(400 * 86_400L), lastMessageAt = ago(60)))
        assertEquals(MaskTimeSection.EARLIER, sectionOf(createdAt = null))
    }

    @Test
    fun `a future timestamp from a skewed clock lands in today`() {
        assertEquals(MaskTimeSection.TODAY, sectionOf(now.plusSeconds(3_600)))
    }

    @Test
    fun `sections are ordered newest first and keep row order`() {
        val masks = listOf(
            mask("old", createdAt = ago(90 * 86_400L)),
            mask("a", createdAt = ago(60)),
            mask("week", createdAt = ago(3 * 86_400L)),
            mask("b", createdAt = ago(120)),
        )

        val sections = groupMasksBySection(masks, nowSec)

        assertEquals(
            listOf(MaskTimeSection.TODAY, MaskTimeSection.THIS_WEEK, MaskTimeSection.EARLIER),
            sections.map { it.section },
        )
        assertEquals(listOf("a", "b"), sections[0].items.map { it.id })
    }
}
