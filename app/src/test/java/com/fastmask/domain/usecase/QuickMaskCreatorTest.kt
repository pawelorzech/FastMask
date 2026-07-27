package com.fastmask.domain.usecase

import com.fastmask.R
import com.fastmask.domain.model.AppMode
import com.fastmask.domain.model.EmailState
import com.fastmask.testutil.FakeAuthRepository
import com.fastmask.testutil.FakeMaskedEmailRepository
import com.fastmask.testutil.FakeQuickMaskGuard
import com.fastmask.ui.common.UiErrors
import java.io.IOException
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/**
 * The decision table behind the Quick Settings tile and the launcher shortcut.
 *
 * Both entry points create a mask with a single tap, from outside the app's
 * own UI — which means every guard the app relies on inside the UI has to be
 * re-asserted here. In particular the biometric app lock: the tile sits on the
 * shade above the lock screen, so a tile that creates and copies an address
 * while the app itself is locked would be a straight bypass of MainActivity's
 * P0 gate.
 */
class QuickMaskCreatorTest {

    private fun httpException(code: Int): HttpException =
        HttpException(
            Response.error<Unit>(code, "".toResponseBody("application/json".toMediaType()))
        )

    private fun creator(
        repo: FakeMaskedEmailRepository = FakeMaskedEmailRepository(),
        auth: FakeAuthRepository = FakeAuthRepository(loggedIn = true),
        guard: FakeQuickMaskGuard = FakeQuickMaskGuard(),
    ) = QuickMaskCreator(
        authRepository = auth,
        guard = guard,
        createMaskedEmailUseCase = CreateMaskedEmailUseCase(repo),
        destroyMaskedEmailUseCase = DestroyMaskedEmailUseCase(repo),
    )

    // --- gates: nothing may be created behind them --------------------------

    @Test
    fun `signed out tap creates nothing and asks for the app`() = runTest {
        val repo = FakeMaskedEmailRepository()

        val result = creator(repo = repo, auth = FakeAuthRepository(loggedIn = false)).create()

        assertEquals(QuickMaskResult.NotSignedIn, result)
        assertEquals(0, repo.createCalls)
    }

    @Test
    fun `demo mode creates nothing even though isLoggedIn reports true`() = runTest {
        val repo = FakeMaskedEmailRepository()

        // The real AuthRepositoryImpl answers isLoggedIn() with true in demo
        // mode. Trusting that alone would route the tile into the demo
        // repository, which happily mints an in-memory address the user would
        // then paste into a real signup form.
        val result = creator(
            repo = repo,
            auth = FakeAuthRepository(loggedIn = true),
            guard = FakeQuickMaskGuard(mode = AppMode.DEMO),
        ).create()

        assertEquals(QuickMaskResult.DemoMode, result)
        assertEquals(0, repo.createCalls)
    }

    @Test
    fun `armed app lock creates nothing and demands an unlock`() = runTest {
        val repo = FakeMaskedEmailRepository()

        val result = creator(
            repo = repo,
            guard = FakeQuickMaskGuard(lockEnabled = true),
        ).create()

        assertEquals(QuickMaskResult.LockRequired, result)
        assertEquals(0, repo.createCalls)
    }

    /*
     * There used to be a second test here asserting the opposite: that the lock
     * preference WITHOUT Pro let quick creation through, on the stated grounds
     * that the gate matched "the same conjunction MainActivity uses:
     * appLockEnabled && Pro". MainActivity did not use that conjunction
     * consistently — it re-locked on the raw preference at ON_STOP — so a user
     * whose purchase lapsed met a lock screen on every resume while the tile
     * minted masks straight to the clipboard past it. Audit 2026-07-27 removed
     * the conjunction from all four sites that carried it.
     *
     * The regression guard is the compiler, not a test: QuickMaskGuard no longer
     * exposes an entitlement at all, so the conjunction cannot be written again
     * without first re-adding the method and arguing for it.
     */

    // --- happy path ---------------------------------------------------------

    @Test
    fun `happy path creates exactly one enabled mask with no prefix`() = runTest {
        val repo = FakeMaskedEmailRepository()

        val result = creator(repo = repo).create()

        assertEquals(1, repo.createCalls)
        assertEquals(EmailState.ENABLED, repo.lastCreateParams?.state)
        assertNull("the tile has no way to ask for a prefix", repo.lastCreateParams?.emailPrefix)
        assertEquals(QuickMaskResult.Created(id = "created", email = "created@fastmail.com"), result)
    }

    // --- failures: a concrete reason, never a mystery ------------------------
    //
    // Failed carries the raw cause, not a string resource: the domain layer
    // must not import `com.fastmask.ui`/`R` (Clean Architecture, CLAUDE.md).
    // The assertions below therefore check both halves of the contract — the
    // cause is reported unchanged, AND the mapping the Android layer
    // (QuickMaskRunner) applies to it still yields the message the user was
    // promised. UiErrors is a pure function, so that mapping is checkable here.

    private fun failureCause(result: QuickMaskResult): Throwable {
        assertTrue("expected a failure, got $result", result is QuickMaskResult.Failed)
        return (result as QuickMaskResult.Failed).cause
    }

    private fun messageFor(result: QuickMaskResult): Int =
        UiErrors.messageRes(failureCause(result), R.string.create_email_error_failed)

    @Test
    fun `network failure reports the network message and no address`() = runTest {
        val offline = IOException("offline")
        val repo = FakeMaskedEmailRepository(failure = offline)

        val result = creator(repo = repo).create()

        assertEquals(QuickMaskResult.Failed(offline), result)
        assertSame(offline, failureCause(result))
        assertEquals(R.string.error_network, messageFor(result))
    }

    @Test
    fun `a rejected token reports the auth message`() = runTest {
        val repo = FakeMaskedEmailRepository(failure = httpException(401))

        assertEquals(R.string.error_auth, messageFor(creator(repo = repo).create()))
    }

    @Test
    fun `rate limiting reports the rate limit message`() = runTest {
        val repo = FakeMaskedEmailRepository(failure = httpException(429))

        assertEquals(R.string.error_rate_limit, messageFor(creator(repo = repo).create()))
    }

    @Test
    fun `a server error reports the server message`() = runTest {
        val repo = FakeMaskedEmailRepository(failure = httpException(503))

        assertEquals(R.string.error_server, messageFor(creator(repo = repo).create()))
    }

    @Test
    fun `an unrecognized failure falls back to the create error message`() = runTest {
        val repo = FakeMaskedEmailRepository(failure = IllegalStateException("boom"))

        assertEquals(R.string.create_email_error_failed, messageFor(creator(repo = repo).create()))
    }

    // --- undo ---------------------------------------------------------------

    @Test
    fun `undo destroys exactly the mask that was just created`() = runTest {
        val repo = FakeMaskedEmailRepository()
        val quickMask = creator(repo = repo)

        val created = quickMask.create() as QuickMaskResult.Created
        val undone = quickMask.undo(created.id)

        assertTrue("undo must report success", undone)
        assertEquals(1, repo.destroyCalls)
        assertEquals("created", repo.lastDestroyId)
    }

    @Test
    fun `undo reports failure when the destroy does not land`() = runTest {
        val repo = FakeMaskedEmailRepository(failure = IOException("offline"))

        assertEquals(false, creator(repo = repo).undo("created"))
        assertEquals(1, repo.destroyCalls)
    }
}
