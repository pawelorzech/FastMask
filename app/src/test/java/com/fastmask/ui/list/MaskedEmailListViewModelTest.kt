package com.fastmask.ui.list

import com.fastmask.R
import com.fastmask.data.local.SettingsDataStore
import com.fastmask.domain.model.AppMode
import com.fastmask.domain.model.EmailState
import com.fastmask.domain.usecase.GetMaskedEmailsUseCase
import com.fastmask.domain.usecase.UpdateMaskedEmailUseCase
import com.fastmask.testutil.FakeMaskedEmailRepository
import com.fastmask.testutil.MainDispatcherRule
import com.fastmask.testutil.mask
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MaskedEmailListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val settings = mockk<SettingsDataStore> {
        every { appModeBlocking() } returns AppMode.REAL
        every { appMode } returns flowOf(AppMode.REAL)
        every { tutorialCompleted } returns flowOf(true)
    }

    private fun vm(repo: FakeMaskedEmailRepository) =
        MaskedEmailListViewModel(
            GetMaskedEmailsUseCase(repo),
            UpdateMaskedEmailUseCase(repo),
            settings,
        )

    private val t1: Instant = Instant.parse("2026-01-01T10:00:00Z")
    private val t2: Instant = Instant.parse("2026-02-01T10:00:00Z")
    private val t3: Instant = Instant.parse("2026-03-01T10:00:00Z")

    // --- sorting -----------------------------------------------------------

    @Test
    fun `list sorts by lastMessageAt desc with createdAt fallback`() = runTest {
        val repo = FakeMaskedEmailRepository(
            emails = listOf(
                mask("oldMail", createdAt = t1, lastMessageAt = t1),
                mask("noMailButNewer", createdAt = t2, lastMessageAt = null),
                mask("newestMail", createdAt = t1, lastMessageAt = t3),
            )
        )
        val viewModel = vm(repo)
        advanceUntilIdle()

        val ids = viewModel.uiState.value.emails.map { it.id }
        assertEquals(listOf("newestMail", "noMailButNewer", "oldMail"), ids)
    }

    // --- filtering (regression: Active chip count vs filtered list) --------

    @Test
    fun `active filter includes pending masks matching the chip count`() = runTest {
        val repo = FakeMaskedEmailRepository(
            emails = listOf(
                mask("enabled", state = EmailState.ENABLED),
                mask("pending", state = EmailState.PENDING),
                mask("off", state = EmailState.DISABLED),
                mask("archived", state = EmailState.DELETED),
            )
        )
        val viewModel = vm(repo)
        advanceUntilIdle()

        viewModel.onFilterChange(EmailFilter.ENABLED)

        val ids = viewModel.uiState.value.filteredEmails.map { it.id }.toSet()
        // Previously PENDING was excluded here while the chip counted it.
        assertEquals(setOf("enabled", "pending"), ids)
    }

    @Test
    fun `disabled and deleted filters match exactly one state`() = runTest {
        val repo = FakeMaskedEmailRepository(
            emails = listOf(
                mask("e", state = EmailState.ENABLED),
                mask("d", state = EmailState.DISABLED),
                mask("x", state = EmailState.DELETED),
            )
        )
        val viewModel = vm(repo)
        advanceUntilIdle()

        viewModel.onFilterChange(EmailFilter.DISABLED)
        assertEquals(listOf("d"), viewModel.uiState.value.filteredEmails.map { it.id })

        viewModel.onFilterChange(EmailFilter.DELETED)
        assertEquals(listOf("x"), viewModel.uiState.value.filteredEmails.map { it.id })
    }

    // --- search ------------------------------------------------------------

    @Test
    fun `search matches email description and domain case-insensitively`() = runTest {
        val repo = FakeMaskedEmailRepository(
            emails = listOf(
                mask("m1", description = "GitHub notifications"),
                mask("m2", forDomain = "shop.example.com"),
                mask("m3"),
            )
        )
        val viewModel = vm(repo)
        advanceUntilIdle()

        viewModel.onSearchQueryChange("github")
        assertEquals(listOf("m1"), viewModel.uiState.value.filteredEmails.map { it.id })

        viewModel.onSearchQueryChange("SHOP.EX")
        assertEquals(listOf("m2"), viewModel.uiState.value.filteredEmails.map { it.id })

        viewModel.onSearchQueryChange("m3@")
        assertEquals(listOf("m3"), viewModel.uiState.value.filteredEmails.map { it.id })
    }

    // --- duplicate fetch guard (regression: double fetch on first entry) ---

    @Test
    fun `refresh during in-flight initial load does not duplicate the request`() = runTest {
        val repo = FakeMaskedEmailRepository(emails = listOf(mask("m1")))
        val viewModel = vm(repo)
        // Init load dispatched but not yet run — mirrors the on-resume refresh
        // firing ~250ms after first composition.
        viewModel.refreshMaskedEmails()
        advanceUntilIdle()

        assertEquals(1, repo.getCalls)
    }

    @Test
    fun `refresh after load completes does fetch again`() = runTest {
        val repo = FakeMaskedEmailRepository(emails = listOf(mask("m1")))
        val viewModel = vm(repo)
        advanceUntilIdle()

        viewModel.refreshMaskedEmails()
        advanceUntilIdle()

        assertEquals(2, repo.getCalls)
    }

    // --- error mapping -----------------------------------------------------

    @Test
    fun `network failure maps to localized network error resource`() = runTest {
        val repo = FakeMaskedEmailRepository(failure = IOException("Unable to resolve host"))
        val viewModel = vm(repo)
        advanceUntilIdle()

        assertEquals(R.string.error_network, viewModel.uiState.value.errorRes)
    }

    @Test
    fun `restoreMask re-enables an archived mask and reloads`() = runTest {
        val repo = FakeMaskedEmailRepository(
            emails = listOf(mask("m1", state = EmailState.DELETED))
        )
        val viewModel = vm(repo)
        advanceUntilIdle()

        viewModel.restoreMask("m1")
        advanceUntilIdle()

        assertEquals(1, repo.updateCalls)
        assertEquals("m1", repo.lastUpdateId)
        assertEquals(EmailState.ENABLED, repo.lastUpdateParams?.state)
        // reload fired after the successful restore
        assertEquals(2, repo.getCalls)
    }

    @Test
    fun `soft refresh with cached data suppresses error`() = runTest {
        val repo = FakeMaskedEmailRepository(emails = listOf(mask("m1")))
        val viewModel = vm(repo)
        advanceUntilIdle()

        repo.failure = IOException("offline now")
        viewModel.refreshMaskedEmails()
        advanceUntilIdle()

        // Old data stays, no error banner over a working list.
        assertEquals(null, viewModel.uiState.value.errorRes)
        assertEquals(listOf("m1"), viewModel.uiState.value.emails.map { it.id })
    }
}
