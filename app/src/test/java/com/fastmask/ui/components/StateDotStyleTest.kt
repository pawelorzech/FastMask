package com.fastmask.ui.components

import com.fastmask.domain.model.EmailState
import org.junit.Assert.assertEquals
import org.junit.Test

class StateDotStyleTest {

    @Test
    fun `every mask state has a distinct non-colour style`() {
        val styles = EmailState.entries.associateWith(::stateDotStyleFor)

        assertEquals(EmailState.entries.size, styles.values.toSet().size)
        assertEquals(StateDotStyle.FILLED_CIRCLE, styles[EmailState.ENABLED])
        assertEquals(StateDotStyle.RING, styles[EmailState.DISABLED])
        assertEquals(StateDotStyle.FILLED_SQUARE, styles[EmailState.DELETED])
        assertEquals(StateDotStyle.DASHED_RING, styles[EmailState.PENDING])
    }
}
