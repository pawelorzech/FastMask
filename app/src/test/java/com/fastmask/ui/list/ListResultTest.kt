package com.fastmask.ui.list

import androidx.lifecycle.SavedStateHandle
import com.fastmask.domain.model.EmailState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ListResultTest {

    private val handle = SavedStateHandle()

    private fun pending(): ListResult? = handle.pendingListResult().value

    @Test
    fun `older snackbar result does not consume a newer archive`() {
        val older = ListResult.Archived("older-a", EmailState.ENABLED)
        val newer = ListResult.Archived("newer-b", EmailState.DISABLED)
        handle.postListResult(older)
        handle.postListResult(newer)

        handle.consumeListResult(older)

        assertEquals(newer, pending())
    }

    @Test
    fun `a result is consumed exactly once`() {
        val archived = ListResult.Archived("current", EmailState.ENABLED)
        handle.postListResult(archived)

        handle.consumeListResult(archived)
        assertNull(pending())

        // A repeat consume must not touch whatever arrives afterwards.
        val created = ListResult.Created("new@fastmail.com")
        handle.postListResult(created)
        handle.consumeListResult(archived)
        assertEquals(created, pending())

        handle.consumeListResult(created)
        assertNull(pending())
    }

    @Test
    fun `a created result survives consuming an unrelated archive`() {
        val created = ListResult.Created("new@fastmail.com")
        handle.postListResult(created)

        handle.consumeListResult(ListResult.Archived("a", EmailState.ENABLED))

        assertEquals(created, pending())
    }

    @Test
    fun `an archive keeps its pre-archive state through the handle`() {
        val archived = ListResult.Archived("a", EmailState.DISABLED)
        handle.postListResult(archived)

        assertEquals(archived, pending())
        assertEquals(EmailState.DISABLED, (pending() as ListResult.Archived).previousState)
    }

    @Test
    fun `two archives of the same mask are different results`() {
        assertNotEquals(
            ListResult.Archived("a", EmailState.ENABLED),
            ListResult.Archived("a", EmailState.ENABLED),
        )
    }
}
