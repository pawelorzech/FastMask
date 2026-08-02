package com.fastmask.ui.navigation

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArchiveResultConsumptionTest {

    @Test
    fun `older snackbar result does not consume a newer archive`() {
        val handle = SavedStateHandle(
            mapOf(
                "archived_mask_id" to "newer-b",
                "archived_mask_state" to "DISABLED",
            ),
        )

        handle.consumeArchivedResultIfCurrent("older-a")

        assertEquals("newer-b", handle.get<String>("archived_mask_id"))
        assertEquals("DISABLED", handle.get<String>("archived_mask_state"))
    }

    @Test
    fun `matching snackbar result consumes its id and previous state`() {
        val handle = SavedStateHandle(
            mapOf(
                "archived_mask_id" to "current",
                "archived_mask_state" to "ENABLED",
            ),
        )

        handle.consumeArchivedResultIfCurrent("current")

        assertNull(handle.get<String>("archived_mask_id"))
        assertNull(handle.get<String>("archived_mask_state"))
    }
}
