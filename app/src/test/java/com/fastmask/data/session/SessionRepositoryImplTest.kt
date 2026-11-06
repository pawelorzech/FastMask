package com.fastmask.data.session

import android.content.Context
import com.fastmask.data.api.JmapApi
import com.fastmask.data.local.ExportCache
import com.fastmask.data.local.MaskedEmailCache
import com.fastmask.data.local.TokenStorage
import com.fastmask.data.repository.DemoMaskedEmailRepositoryImpl
import com.fastmask.domain.model.CreateMaskedEmailParams
import com.fastmask.domain.model.Session
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SessionRepositoryImplTest {

    @get:Rule
    val temp = TemporaryFolder()

    private var hasToken = false
    private var storedDemo = false

    private val tokenStorage = mockk<TokenStorage> {
        every { hasToken() } answers { hasToken }
        every { saveToken(any()) } answers { hasToken = true }
        every { clearToken() } answers { hasToken = false }
    }
    private val jmapApi = mockk<JmapApi> {
        justRun { clearSession() }
        coEvery { getSession(any()) } returns Result.success(mockk(relaxed = true))
    }
    private val store = mockk<SessionStore> {
        coEvery { isDemo() } answers { storedDemo }
        coEvery { startDemo() } answers { storedDemo = true }
        coEvery { endDemo() } answers { storedDemo = false }
        every { tutorialCompleted } returns flowOf(false)
        coJustRun { completeTutorial() }
    }
    private val maskCache = mockk<MaskedEmailCache>(relaxed = true)
    private val demoRepository = DemoMaskedEmailRepositoryImpl()

    private val exportCache: ExportCache by lazy {
        ExportCache(mockk<Context> { every { cacheDir } returns temp.newFolder("cache") })
    }

    private fun repository() = SessionRepositoryImpl(
        tokenStorage = tokenStorage,
        jmapApi = jmapApi,
        store = store,
        demoRepository = demoRepository,
        exportCache = exportCache,
        maskCache = maskCache,
        ioDispatcher = UnconfinedTestDispatcher(),
    )

    @Test
    fun `a stored demo is signed in without opening encrypted token storage`() = runTest {
        storedDemo = true

        assertEquals(Session.DEMO, repository().current())
        verify(exactly = 0) { tokenStorage.hasToken() }
    }

    @Test
    fun `a stored token is the real account`() = runTest {
        hasToken = true

        assertEquals(Session.ACCOUNT, repository().current())
    }

    @Test
    fun `nothing stored is signed out`() = runTest {
        assertEquals(Session.SIGNED_OUT, repository().current())
    }

    @Test
    fun `an unreadable mode preference falls back to the real account, never the demo`() = runTest {
        coEvery { store.isDemo() } throws java.io.IOException("unreadable")
        hasToken = true

        assertEquals(Session.ACCOUNT, repository().current())
    }

    @Test
    fun `sign in stores the token and publishes the account`() = runTest {
        val repository = repository()

        assertTrue(repository.signIn("fmu1-token").isSuccess)

        assertTrue(hasToken)
        assertEquals(Session.ACCOUNT, repository.session.value)
    }

    @Test
    fun `a rejected token stores nothing and stays signed out`() = runTest {
        coEvery { jmapApi.getSession(any()) } returns Result.failure(java.io.IOException("401"))
        val repository = repository()

        assertTrue(repository.signIn("bad").isFailure)

        assertFalse(hasToken)
        assertEquals(Session.SIGNED_OUT, repository.current())
    }

    @Test
    fun `sign in drops the previous account's exports and snapshot`() = runTest {
        val export = exportCache.write("email\nold@fastmail.com\n", generation = exportCache.currentGeneration())

        repository().signIn("fmu1-token")

        assertFalse(export.exists())
        verify { maskCache.clear() }
    }

    @Test
    fun `sign out drops the token, exports and snapshot`() = runTest {
        hasToken = true
        val repository = repository()
        val export = exportCache.write("email\na@fastmail.com\n", generation = exportCache.currentGeneration())

        repository.signOut()

        assertFalse(hasToken)
        assertFalse(export.exists())
        verify { maskCache.clear() }
        verify { jmapApi.clearSession() }
        assertEquals(Session.SIGNED_OUT, repository.session.value)
    }

    @Test
    fun `signing out of the demo ends it`() = runTest {
        val repository = repository()
        repository.enterDemo()

        repository.signOut()

        assertFalse(storedDemo)
        assertEquals(Session.SIGNED_OUT, repository.current())
    }

    @Test
    fun `every demo starts from the seed list`() = runTest {
        val repository = repository()
        repository.enterDemo()
        val seed = demoRepository.getMaskedEmails().getOrThrow()
        demoRepository.createMaskedEmail(CreateMaskedEmailParams())
        repository.signOut()

        repository.enterDemo()

        assertEquals(seed, demoRepository.getMaskedEmails().getOrThrow())
        assertEquals(Session.DEMO, repository.session.value)
    }
}
