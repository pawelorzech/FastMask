package com.fastmask.ui.list

import com.fastmask.domain.model.EmailState
import com.fastmask.domain.model.MaskedEmail
import org.junit.Assert.assertEquals
import org.junit.Test

class EmailFilterTest {

    private fun mask(id: String, state: EmailState) = MaskedEmail(
        id = id, email = "$id@fastmail.com", state = state,
        forDomain = null, description = null, createdBy = null,
        url = null, emailPrefix = null, createdAt = null, lastMessageAt = null,
    )

    private val masks = listOf(
        mask("a", EmailState.ENABLED),
        mask("b", EmailState.PENDING),
        mask("c", EmailState.DISABLED),
        mask("d", EmailState.DELETED),
        mask("e", EmailState.ENABLED),
    )

    @Test
    fun `counts are exactly the sizes of what each filter admits`() {
        val counts = EmailFilter.countsOf(masks)
        EmailFilter.entries.forEach { filter ->
            assertEquals(filter.name, masks.filter(filter::matches).size, counts[filter])
        }
    }

    @Test
    fun `Active admits PENDING as well as ENABLED`() {
        assertEquals(listOf("a", "b", "e"), masks.filter(EmailFilter.ENABLED::matches).map { it.id })
        assertEquals(3, EmailFilter.countsOf(masks)[EmailFilter.ENABLED])
        assertEquals(5, EmailFilter.countsOf(masks)[EmailFilter.ALL])
    }
}
