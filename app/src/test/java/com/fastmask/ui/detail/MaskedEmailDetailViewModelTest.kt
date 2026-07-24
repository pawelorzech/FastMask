@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.fastmask.ui.detail

import androidx.lifecycle.SavedStateHandle
import com.fastmask.domain.model.EmailState
import com.fastmask.domain.usecase.DeleteMaskedEmailUseCase
import com.fastmask.domain.usecase.GetMaskedEmailsUseCase
import com.fastmask.domain.usecase.UpdateMaskedEmailUseCase
import com.fastmask.testutil.FakeMaskedEmailRepository
import com.fastmask.testutil.MainDispatcherRule
import com.fastmask.testutil.mask
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class MaskedEmailDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun vm(repo: FakeMaskedEmailRepository, emailId: String = "m1") =
        MaskedEmailDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("emailId" to emailId)),
            getMaskedEmailsUseCase = GetMaskedEmailsUseCase(repo),
            updateMaskedEmailUseCase = UpdateMaskedEmailUseCase(repo),
            deleteMaskedEmailUseCase = DeleteMaskedEmailUseCase(repo),
        )

    // --- clearing fields (regression: cleared fields silently reverted) ----

    @Test
    fun `clearing description sends empty string not null`() = runTest {
        val repo = FakeMaskedEmailRepository(
            emails = listOf(mask("m1", description = "old note", forDomain = "example.com"))
        )
        val viewModel = vm(repo)
        advanceUntilIdle() // initial load

        viewModel.onDescriptionChange("")
        viewModel.saveChanges()
        advanceUntilIdle()

        assertEquals(1, repo.updateCalls)
        // "" = clear on the server; null would mean "unchanged" and the old
        // description would silently survive.
        assertEquals("", repo.lastUpdateParams?.description)
        // Unchanged fields are NOT sent.
        assertNull(repo.lastUpdateParams?.forDomain)
        assertNull(repo.lastUpdateParams?.url)
    }

    @Test
    fun `only changed fields are included in update params`() = runTest {
        val repo = FakeMaskedEmailRepository(
            emails = listOf(mask("m1", description = "note", forDomain = "example.com", url = "https://example.com"))
        )
        val viewModel = vm(repo)
        advanceUntilIdle()

        viewModel.onForDomainChange("other.com")
        viewModel.saveChanges()
        advanceUntilIdle()

        assertEquals(1, repo.updateCalls)
        assertEquals("other.com", repo.lastUpdateParams?.forDomain)
        assertNull(repo.lastUpdateParams?.description)
        assertNull(repo.lastUpdateParams?.url)
    }

    @Test
    fun `save with no changes performs no network call`() = runTest {
        val repo = FakeMaskedEmailRepository(emails = listOf(mask("m1", description = "note")))
        val viewModel = vm(repo)
        advanceUntilIdle()

        viewModel.saveChanges()
        advanceUntilIdle()

        assertEquals(0, repo.updateCalls)
    }

    // --- double-tap guards -------------------------------------------------

    @Test
    fun `rapid double delete fires exactly one request`() = runTest {
        val repo = FakeMaskedEmailRepository(emails = listOf(mask("m1")))
        val viewModel = vm(repo)
        advanceUntilIdle()

        viewModel.delete()
        viewModel.delete() // second tap in the same frame
        advanceUntilIdle()

        assertEquals(1, repo.deleteCalls)
    }

    @Test
    fun `toggle state updates and reloads on success`() = runTest {
        val repo = FakeMaskedEmailRepository(emails = listOf(mask("m1", state = EmailState.ENABLED)))
        val viewModel = vm(repo)
        advanceUntilIdle()

        viewModel.toggleState()
        advanceUntilIdle()

        assertEquals(1, repo.updateCalls)
        assertEquals(EmailState.DISABLED, repo.lastUpdateParams?.state)
    }
}
