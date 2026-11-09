package com.fastmask.quickmask

import com.fastmask.R
import com.fastmask.domain.model.Session
import com.fastmask.domain.model.EmailState
import com.fastmask.testutil.FakeAppLockSetting
import com.fastmask.testutil.FakeMaskedEmailRepository
import com.fastmask.testutil.FakeSessionRepository
import com.fastmask.ui.common.UiErrors
import java.io.IOException
import kotlinx.coroutines.test.runTest
import com.fastmask.testutil.failureForStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

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

    private fun creator(
        repo: FakeMaskedEmailRepository = FakeMaskedEmailRepository(),
        session: Session = Session.ACCOUNT,
        lock: FakeAppLockSetting = FakeAppLockSetting(),
    ) = QuickMaskCreator(
        sessionRepository = FakeSessionRepository(initial = session),
        appLockSetting = lock,
        maskedEmailRepository = repo,
    )

    // --- gates: nothing may be created behind them --------------------------

    @Test
    fun `signed out tap creates nothing and asks for the app`() = runTest {
        val repo = FakeMaskedEmailRepository()

        val result = creator(repo = repo, session = Session.SIGNED_OUT).create()

        assertEquals(QuickMaskResult.NotSignedIn, result)
        assertEquals(0, repo.createCalls)
    }

    @Test
    fun `demo mode creates nothing even though it counts as signed in`() = runTest {
        val repo = FakeMaskedEmailRepository()

        // A demo mask minted in memory would end up pasted into a real signup form.
        val result = creator(repo = repo, session = Session.DEMO).create()

        assertEquals(QuickMaskResult.DemoMode, result)
        assertEquals(0, repo.createCalls)
    }

    @Test
    fun `armed app lock creates nothing and demands an unlock`() = runTest {
        val repo = FakeMaskedEmailRepository()

        val result = creator(
            repo = repo,
            lock = FakeAppLockSetting(enabled = true),
        ).create()

        assertEquals(QuickMaskResult.LockRequired, result)
        assertEquals(0, repo.createCalls)
    }

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
        UiErrors.messageRes(failureCause(result), R.string.error_create_mask)

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
        val repo = FakeMaskedEmailRepository(failure = failureForStatus(401))

        assertEquals(R.string.error_auth, messageFor(creator(repo = repo).create()))
    }

    @Test
    fun `rate limiting reports the rate limit message`() = runTest {
        val repo = FakeMaskedEmailRepository(failure = failureForStatus(429))

        assertEquals(R.string.error_rate_limit, messageFor(creator(repo = repo).create()))
    }

    @Test
    fun `a server error reports the server message`() = runTest {
        val repo = FakeMaskedEmailRepository(failure = failureForStatus(503))

        assertEquals(R.string.error_server, messageFor(creator(repo = repo).create()))
    }

    @Test
    fun `an unrecognized failure falls back to the create error message`() = runTest {
        val repo = FakeMaskedEmailRepository(failure = IllegalStateException("boom"))

        assertEquals(R.string.error_create_mask, messageFor(creator(repo = repo).create()))
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
