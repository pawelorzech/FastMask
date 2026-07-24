@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.fastmask.ui.create

import com.fastmask.domain.model.EmailState
import com.fastmask.domain.usecase.CreateMaskedEmailUseCase
import com.fastmask.testutil.FakeMaskedEmailRepository
import com.fastmask.testutil.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import com.fastmask.R
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
        assertNull(viewModel.uiState.value.prefixErrorRes)
    }

    @Test
    fun `underscores and digits survive sanitization`() = runTest {
        val viewModel = vm(FakeMaskedEmailRepository())

        viewModel.onPrefixChange("shop_2026")

        assertEquals("shop_2026", viewModel.uiState.value.emailPrefix)
    }

    @Test
    fun `unicode-letter prefix surfaces a localizable char error`() = runTest {
        val viewModel = vm(FakeMaskedEmailRepository())

        // 'é' passes isLetterOrDigit() (so it survives the filter) but fails the
        // ASCII regex → the char-validation message must fire, as a string res.
        viewModel.onPrefixChange("café")

        assertEquals(
            R.string.create_email_error_prefix_chars,
            viewModel.uiState.value.prefixErrorRes,
        )
    }

    @Test
    fun `over-long prefix surfaces a localizable length error`() = runTest {
        val viewModel = vm(FakeMaskedEmailRepository())

        viewModel.onPrefixChange("a".repeat(65))

        assertEquals(
            R.string.create_email_error_prefix_length,
            viewModel.uiState.value.prefixErrorRes,
        )
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
