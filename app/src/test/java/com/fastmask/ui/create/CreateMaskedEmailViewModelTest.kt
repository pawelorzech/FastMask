@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.fastmask.ui.create

import com.fastmask.domain.model.EmailState
import com.fastmask.domain.share.SharePrefill
import com.fastmask.domain.usecase.CreateMaskedEmailUseCase
import com.fastmask.testutil.FakeMaskedEmailRepository
import com.fastmask.testutil.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import com.fastmask.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import kotlinx.coroutines.CoroutineScope
import org.junit.Test

class CreateMaskedEmailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // Shares the rule's scheduler, so the mutation the ViewModel hands to the
    // application scope still advances under runTest's virtual time.
    private fun appScope() = CoroutineScope(mainDispatcherRule.dispatcher)

    private fun vm(repo: FakeMaskedEmailRepository) =
        CreateMaskedEmailViewModel(CreateMaskedEmailUseCase(repo), appScope())

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

    // --- share prefill ------------------------------------------------------

    private val githubPrefill = SharePrefill(
        forDomain = "github.com",
        url = "https://www.github.com/signup",
        description = "github.com",
    )

    @Test
    fun `share prefill lands in the form state`() = runTest {
        val viewModel = vm(FakeMaskedEmailRepository())

        viewModel.applyPrefill(githubPrefill)

        val state = viewModel.uiState.value
        assertEquals("github.com", state.forDomain)
        assertEquals("https://www.github.com/signup", state.url)
        assertEquals("github.com", state.description)
        assertTrue(state.prefilled)
    }

    @Test
    fun `prefilled form still creates with the shared values`() = runTest {
        val repo = FakeMaskedEmailRepository()
        val viewModel = vm(repo)
        viewModel.applyPrefill(githubPrefill)

        viewModel.create()
        advanceUntilIdle()

        assertEquals("github.com", repo.lastCreateParams?.forDomain)
        assertEquals("https://www.github.com/signup", repo.lastCreateParams?.url)
        assertEquals("github.com", repo.lastCreateParams?.description)
        // A share never asks for a prefix, and it must not force a state.
        assertNull(repo.lastCreateParams?.emailPrefix)
        assertEquals(EmailState.ENABLED, repo.lastCreateParams?.state)
    }

    @Test
    fun `a null prefill is a no-op`() = runTest {
        val viewModel = vm(FakeMaskedEmailRepository())

        // Shared text with no link at all: the screen still opens, empty.
        viewModel.applyPrefill(null)

        val state = viewModel.uiState.value
        assertEquals("", state.forDomain)
        assertEquals("", state.url)
        assertEquals("", state.description)
        assertFalse(state.prefilled)
    }

    @Test
    fun `a second prefill never overwrites what the user typed`() = runTest {
        val viewModel = vm(FakeMaskedEmailRepository())
        viewModel.applyPrefill(githubPrefill)
        viewModel.onDescriptionChange("GitHub work account")

        // The screen re-delivers the nav argument on every recomposition and
        // after a rotation; re-applying it would silently discard the edit.
        viewModel.applyPrefill(githubPrefill)

        assertEquals("GitHub work account", viewModel.uiState.value.description)
    }

    // --- regression: the screen without a share behaves exactly as before ----

    @Test
    fun `without a prefill the form starts empty`() = runTest {
        val state = vm(FakeMaskedEmailRepository()).uiState.value

        assertEquals("", state.emailPrefix)
        assertEquals("", state.forDomain)
        assertEquals("", state.description)
        assertEquals("", state.url)
        assertEquals(EmailState.ENABLED, state.initialState)
        assertFalse(state.prefilled)
    }
}
