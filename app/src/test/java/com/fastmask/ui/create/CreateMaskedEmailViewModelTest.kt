@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.fastmask.ui.create

import com.fastmask.domain.model.EmailState
import androidx.lifecycle.SavedStateHandle
import com.fastmask.testutil.FakeMaskedEmailRepository
import com.fastmask.testutil.MainDispatcherRule
import com.fastmask.testutil.failureForStatus
import com.fastmask.ui.common.UiError
import com.fastmask.ui.common.UiErrorAction
import java.io.IOException
import com.fastmask.ui.navigation.NavRoutes
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import com.fastmask.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CreateMaskedEmailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun vm(repo: FakeMaskedEmailRepository, forDomain: String? = null) =
        CreateMaskedEmailViewModel(
            SavedStateHandle(
                if (forDomain == null) emptyMap() else mapOf(NavRoutes.ARG_FOR_DOMAIN to forDomain),
            ),
            repo,
        )

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
    fun `create params are trimmed like the detail editor's save`() = runTest {
        val repo = FakeMaskedEmailRepository()
        val viewModel = vm(repo)
        viewModel.onDescriptionChange("  newsletter \n")
        viewModel.onUrlChange(" https://example.com ")

        viewModel.create()
        advanceUntilIdle()

        assertEquals("newsletter", repo.lastCreateParams?.description)
        assertEquals("https://example.com", repo.lastCreateParams?.url)
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

    // --- failure presentation ----------------------------------------------

    @Test
    fun `refused token asks to sign in again`() = runTest {
        val viewModel = vm(FakeMaskedEmailRepository(failure = failureForStatus(401)))

        viewModel.create()
        advanceUntilIdle()

        assertEquals(
            UiError(R.string.error_auth, UiErrorAction.REAUTHENTICATE),
            viewModel.uiState.value.error,
        )
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `network failure offers a retry and an unknown one does not`() = runTest {
        val repo = FakeMaskedEmailRepository(failure = IOException("offline"))
        val viewModel = vm(repo)

        viewModel.create()
        advanceUntilIdle()
        assertEquals(UiErrorAction.RETRY, viewModel.uiState.value.error?.action)

        repo.failure = RuntimeException("boom")
        viewModel.create()
        advanceUntilIdle()
        assertEquals(
            UiError(R.string.create_email_error_failed, UiErrorAction.NONE),
            viewModel.uiState.value.error,
        )
    }

    // --- share prefill ------------------------------------------------------

    private val github = "https://www.github.com"

    @Test
    fun `share prefill lands in the form state`() = runTest {
        val viewModel = vm(FakeMaskedEmailRepository(), forDomain = github)

        val state = viewModel.uiState.value
        assertEquals("https://www.github.com", state.forDomain)
        assertEquals("", state.url)
        assertEquals("", state.description)
    }

    @Test
    fun `prefilled form still creates with the shared values`() = runTest {
        val repo = FakeMaskedEmailRepository()
        val viewModel = vm(repo, forDomain = github)

        viewModel.create()
        advanceUntilIdle()

        assertEquals("https://www.github.com", repo.lastCreateParams?.forDomain)
        assertNull(repo.lastCreateParams?.url)
        assertNull(repo.lastCreateParams?.description)
        // A share never asks for a prefix, and it must not force a state.
        assertNull(repo.lastCreateParams?.emailPrefix)
        assertEquals(EmailState.ENABLED, repo.lastCreateParams?.state)
    }

    @Test
    fun `a blank forDomain argument leaves the form empty`() = runTest {
        // Shared text with no link at all: the route carries "" and the screen opens empty.
        for (arg in listOf("", "   ")) {
            val state = vm(FakeMaskedEmailRepository(), forDomain = arg).uiState.value

            assertEquals("", state.forDomain)
            assertEquals("", state.url)
            assertEquals("", state.description)
        }
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
    }

    // --- the discard guard reads the state, not the screen -----------------

    @Test
    fun `any typed field makes the form dirty`() = runTest {
        val clean = CreateMaskedEmailUiState()
        assertFalse(clean.isDirty)
        assertEquals(true, clean.copy(emailPrefix = "a").isDirty)
        assertEquals(true, clean.copy(forDomain = "a.com").isDirty)
        assertEquals(true, clean.copy(description = "d").isDirty)
        assertEquals(true, clean.copy(url = "https://a.com").isDirty)
        // Picking Disabled alone is not typed input worth a discard dialog.
        assertFalse(clean.copy(initialState = EmailState.DISABLED).isDirty)
    }
}
