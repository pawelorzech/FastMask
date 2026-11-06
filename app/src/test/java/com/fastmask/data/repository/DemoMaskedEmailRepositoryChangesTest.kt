package com.fastmask.data.repository

import com.fastmask.domain.model.CreateMaskedEmailParams
import com.fastmask.domain.model.EmailState
import com.fastmask.domain.model.UpdateMaskedEmailParams
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DemoMaskedEmailRepositoryChangesTest {

    @Test
    fun `every successful demo mutation announces a change and a failed one does not`() = runTest {
        val repo = DemoMaskedEmailRepositoryImpl()
        var changes = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repo.changes.collect { changes++ } }
        val id = repo.getMaskedEmails().getOrThrow().first().id

        repo.updateMaskedEmail("missing", UpdateMaskedEmailParams(state = EmailState.DISABLED))
        repo.archiveMaskedEmail("missing")
        repo.destroyMaskedEmail("missing")
        assertEquals(0, changes)

        repo.createMaskedEmail(CreateMaskedEmailParams())
        assertEquals(1, changes)
        repo.updateMaskedEmail(id, UpdateMaskedEmailParams(state = EmailState.DISABLED))
        assertEquals(2, changes)
        repo.archiveMaskedEmail(id)
        assertEquals(3, changes)
        repo.destroyMaskedEmail(id)
        assertEquals(4, changes)
    }
}
