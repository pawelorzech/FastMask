package com.fastmask.ui.list

import com.fastmask.R
import org.junit.Assert.assertEquals
import org.junit.Test

class ListErrorActionTest {

    @Test
    fun `rejected token routes to reauthentication`() {
        assertEquals(
            ListErrorAction.REAUTHENTICATE,
            listErrorActionFor(R.string.error_auth),
        )
    }

    @Test
    fun `network and generic failures remain retryable`() {
        assertEquals(ListErrorAction.RETRY, listErrorActionFor(R.string.error_network))
        assertEquals(ListErrorAction.RETRY, listErrorActionFor(R.string.error_load_emails))
    }
}
