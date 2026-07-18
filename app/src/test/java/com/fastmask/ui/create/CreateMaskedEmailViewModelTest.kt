package com.fastmask.ui.create

import com.fastmask.domain.model.EmailState
import com.fastmask.domain.usecase.CreateMaskedEmailUseCase
import com.fastmask.testutil.FakeMaskedEmailRepository
import com.fastmask.testutil.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class CreateMaskedEmailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun vm(repo: FakeMaskedEmailRepository) =
        CreateMaskedEmailViewModel(CreateMaskedEmailUseCase(repo))

    // --- double-tap guard (regression: duplicate masks created) ------------

    @Test
    fun `rapid double tap creates exactly one mask`() = runTest {
        val repo = FakeMaskedEmailRepository()
        val viewModel = vm(repo)
        viewModel.onDescriptionChange("newsletter")

        viewModel.create()
        viewModel.create() // second tap in the same frame
        advanceUntilIdle()

        assertEquals(1, repo.createCalls)
    }

    // --- prefix sanitization -----------------------------------------------

    @Test
    fun `prefix is lowercased and stripped of illegal characters`() = runTest {
        val viewModel = vm(FakeMaskedEmailRepository())

        viewModel.onPrefixChange("My.Shop-Name!")

        assertEquals("myshopname", viewModel.uiState.value.emailPrefix)
        assertNull(viewModel.uiState.value.prefixError)
    }

    @Test
    fun `underscores and digits survive sanitization`() = runTest {
        val viewModel = vm(FakeMaskedEmailRepository())

        viewModel.onPrefixChange("shop_2026")

        assertEquals("shop_2026", viewModel.uiState.value.emailPrefix)
    }

    // --- params construction -----------------------------------------------

    @Test
    fun `blank optional fields are omitted from create params`() = runTest {
        val repo = FakeMaskedEmailRepository()
        val viewModel = vm(repo)
        viewModel.onDescriptionChange("  ")
        viewModel.onDomainChange("")

        viewModel.create()
        advanceUntilIdle()

        assertNull(repo.lastCreateParams?.description)
        assertNull(repo.lastCreateParams?.forDomain)
        assertEquals(EmailState.ENABLED, repo.lastCreateParams?.state)
    }

    @Test
    fun `chosen initial state is passed through`() = runTest {
        val repo = FakeMaskedEmailRepository()
        val viewModel = vm(repo)
        viewModel.onStateChange(EmailState.DISABLED)

        viewModel.create()
        advanceUntilIdle()

        assertEquals(EmailState.DISABLED, repo.lastCreateParams?.state)
    }
}
