package com.fastmask.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class EmailStateTest {

    @Test
    fun `wire names are Fastmail's JMAP values`() {
        assertEquals(
            listOf("pending", "enabled", "disabled", "deleted"),
            EmailState.entries.map { it.wire },
        )
    }
}
