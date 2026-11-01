@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.fastmask.ui.detail

import androidx.lifecycle.SavedStateHandle
import com.fastmask.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import com.fastmask.domain.model.CachedMasks
import com.fastmask.domain.model.EmailState
import com.fastmask.domain.usecase.ArchiveMaskedEmailUseCase
import com.fastmask.domain.usecase.GetCachedMaskedEmailsUseCase
import com.fastmask.domain.usecase.GetMaskedEmailUseCase
import com.fastmask.domain.usecase.UpdateMaskedEmailUseCase
import com.fastmask.testutil.FakeMaskedEmailRepository
import com.fastmask.testutil.MainDispatcherRule
import com.fastmask.testutil.mask
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.io.IOException

class MaskedEmailDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun vm(repo: FakeMaskedEmailRepository, emailId: String = "m1") =
        MaskedEmailDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("emailId" to emailId)),
            getMaskedEmailUseCase = GetMaskedEmailUseCase(repo),
            getCachedMaskedEmailsUseCase = GetCachedMaskedEmailsUseCase(repo),
            updateMaskedEmailUseCase = UpdateMaskedEmailUseCase(repo),
            archiveMaskedEmailUseCase = ArchiveMaskedEmailUseCase(repo),
            // Shares the rule's scheduler so application-scoped mutations still
            // advance under runTest's virtual time.
            appScope = CoroutineScope(mainDispatcherRule.dispatcher),
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

    @Test
    fun `dirty state and save request agree on trimming clearing and missing email`() {
        val unchanged = MaskedEmailDetailUiState(
            email = mask("m1", description = "note"),
            editedDescription = "  note  ",
        )
        assertEquals(false, unchanged.hasChanges)
        assertNull(unchanged.pendingChanges)

        val cleared = unchanged.copy(editedDescription = "   ")
        assertEquals(true, cleared.hasChanges)
        assertEquals("", cleared.pendingChanges?.description)
        assertNull(cleared.pendingChanges?.forDomain)
        assertNull(cleared.pendingChanges?.url)

        val missing = cleared.copy(email = null)
        assertEquals(false, missing.hasChanges)
        assertNull(missing.pendingChanges)
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

        assertEquals(1, repo.archiveCalls)
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

    @Test
    fun `toggle exposes only the toggle loading operation`() = runTest {
        val releaseUpdate = CompletableDeferred<Unit>()
        val repo = FakeMaskedEmailRepository(emails = listOf(mask("m1"))).apply {
            beforeUpdate = { releaseUpdate.await() }
        }
        val viewModel = vm(repo)
        advanceUntilIdle()

        viewModel.toggleState()
        runCurrent()

        assertEquals(MaskedEmailDetailUpdate.TOGGLE_STATE, viewModel.uiState.value.updateOperation)
        releaseUpdate.complete(Unit)
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.updateOperation)
    }

    @Test
    fun `save exposes only the save loading operation`() = runTest {
        val releaseUpdate = CompletableDeferred<Unit>()
        val repo = FakeMaskedEmailRepository(emails = listOf(mask("m1", description = "old"))).apply {
            beforeUpdate = { releaseUpdate.await() }
        }
        val viewModel = vm(repo)
        advanceUntilIdle()
        viewModel.onDescriptionChange("new")

        viewModel.saveChanges()
        runCurrent()

        assertEquals(MaskedEmailDetailUpdate.SAVE_CHANGES, viewModel.uiState.value.updateOperation)
        releaseUpdate.complete(Unit)
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.updateOperation)
    }

    // --- in-progress edits survive a post-action reload (regression) --------

    @Test
    fun `post-toggle reload keeps in-progress edits`() = runTest {
        val repo = FakeMaskedEmailRepository(
            emails = listOf(mask("m1", state = EmailState.ENABLED, description = "old note"))
        )
        val viewModel = vm(repo)
        advanceUntilIdle() // initial load seeds editedDescription = "old note"

        // User is mid-edit, then toggles state; the toggle's success reload must
        // NOT clobber the half-typed description back to the server value.
        viewModel.onDescriptionChange("half-typed draft")
        viewModel.toggleState()
        advanceUntilIdle()

        assertEquals("half-typed draft", viewModel.uiState.value.editedDescription)
    }

    @Test
    fun `initial load still seeds edit fields from the server`() = runTest {
        val repo = FakeMaskedEmailRepository(
            emails = listOf(mask("m1", description = "seed note", forDomain = "seed.com"))
        )
        val viewModel = vm(repo)
        advanceUntilIdle()

        assertEquals("seed note", viewModel.uiState.value.editedDescription)
        assertEquals("seed.com", viewModel.uiState.value.editedForDomain)
    }

    // --- stale-while-revalidate load (regression: full-list fetch per open) --

    @Test
    fun `initial load uses a single-mask fetch not the whole list`() = runTest {
        val repo = FakeMaskedEmailRepository(emails = listOf(mask("m1")))
        val viewModel = vm(repo)
        advanceUntilIdle()

        assertEquals(1, repo.getSingleCalls)
        assertEquals("m1", repo.lastGetSingleId)
        assertEquals(0, repo.getCalls)
    }

    @Test
    fun `cache seed renders the mask before the network answers`() = runTest {
        val releaseNetwork = CompletableDeferred<Unit>()
        val repo = FakeMaskedEmailRepository(
            emails = listOf(mask("m1", description = "fresh")),
            cached = CachedMasks(
                masks = listOf(mask("m1", description = "cached copy")),
                cachedAt = Instant.now(),
            ),
        ).apply {
            beforeGetSingle = { releaseNetwork.await() }
        }
        val viewModel = vm(repo)
        runCurrent() // cache seed has run; network fetch is parked

        assertEquals("cached copy", viewModel.uiState.value.email?.description)
        assertNull(viewModel.uiState.value.errorRes)

        releaseNetwork.complete(Unit)
        advanceUntilIdle()
        // The fresh answer wins once it lands.
        assertEquals("fresh", viewModel.uiState.value.email?.description)
    }

    @Test
    fun `network refresh preserves edited fields and refreshes untouched fields`() = runTest {
        for (field in listOf("description", "domain", "url")) {
            val releaseNetwork = CompletableDeferred<Unit>()
            val repo = FakeMaskedEmailRepository(
                emails = listOf(mask("m1", description = "fresh", forDomain = "fresh.com", url = "https://fresh.com")),
                cached = CachedMasks(
                    masks = listOf(mask("m1", description = "cached", forDomain = "cached.com", url = "https://cached.com")),
                    cachedAt = Instant.now(),
                ),
            ).apply { beforeGetSingle = { releaseNetwork.await() } }
            val viewModel = vm(repo)
            runCurrent()

            when (field) {
                "description" -> viewModel.onDescriptionChange("draft")
                "domain" -> viewModel.onForDomainChange("draft.com")
                "url" -> viewModel.onUrlChange("") // Clearing is also an edit.
            }
            releaseNetwork.complete(Unit)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("fresh", state.email?.description)
            assertEquals(if (field == "description") "draft" else "fresh", state.editedDescription)
            assertEquals(if (field == "domain") "draft.com" else "fresh.com", state.editedForDomain)
            assertEquals(if (field == "url") "" else "https://fresh.com", state.editedUrl)
        }
    }

    // A save that lands while the first network load is still parked used to skip
    // its post-save reload (loadInFlight), so nothing refreshed after the write and
    // the screen kept whatever the earlier, older fetch returned. The reload is
    // now queued behind the in-flight one.
    @Test
    fun `a save during an in-flight load still reloads afterwards`() = runTest {
        val release = CompletableDeferred<Unit>()
        val repo = FakeMaskedEmailRepository(
            emails = listOf(mask("m1", description = "cached copy")),
            cached = CachedMasks(masks = listOf(mask("m1", description = "cached copy")), cachedAt = Instant.now()),
        ).apply { beforeGetSingle = { if (getSingleCalls == 1) release.await() } }
        val viewModel = vm(repo)
        runCurrent() // cache seed rendered; first network load parked

        viewModel.onDescriptionChange("edited")
        viewModel.saveChanges()
        runCurrent()
        assertEquals(1, repo.updateCalls)
        assertEquals(1, repo.getSingleCalls)

        repo.emails = listOf(mask("m1", description = "edited"))
        release.complete(Unit)
        advanceUntilIdle()

        assertEquals("the post-save reload must still run", 2, repo.getSingleCalls)
        assertNull(viewModel.uiState.value.updateOperation)
        assertEquals("edited", viewModel.uiState.value.email?.description)
    }

    @Test
    fun `network failure keeps cached content and degrades to inline error`() = runTest {
        val repo = FakeMaskedEmailRepository(
            emails = listOf(mask("m1", description = "cached copy")),
            cached = CachedMasks(masks = listOf(mask("m1")), cachedAt = Instant.now()),
            failure = IOException("offline"),
        )
        val viewModel = vm(repo)
        advanceUntilIdle()

        // The user still has something to look at and edit…
        assertEquals("m1", viewModel.uiState.value.email?.id)
        // …and the failure is surfaced, not swallowed.
        assertEquals(R.string.error_network, viewModel.uiState.value.errorRes)
    }

    @Test
    fun `mask missing on the server shows the load error state`() = runTest {
        val repo = FakeMaskedEmailRepository(
            emails = emptyList(),
            singleResult = Result.success(null),
        )
        val viewModel = vm(repo)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.email)
        assertEquals(R.string.email_detail_error_load, viewModel.uiState.value.errorRes)
    }
}
