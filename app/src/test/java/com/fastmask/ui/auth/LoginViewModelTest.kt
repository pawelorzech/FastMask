@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.fastmask.ui.auth

import com.fastmask.R
import com.fastmask.domain.auth.MaskedEmailScopeMissingException
import com.fastmask.domain.auth.TokenFormat
import com.fastmask.domain.usecase.LoginUseCase
import com.fastmask.testutil.FakeAuthRepository
import com.fastmask.testutil.FakeDemoModeActivator
import com.fastmask.testutil.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class LoginViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun vm(
        auth: FakeAuthRepository,
        demo: FakeDemoModeActivator = FakeDemoModeActivator(),
    ) = LoginViewModel(LoginUseCase(auth), demo)

    private fun httpException(code: Int): HttpException =
        HttpException(
            Response.error<Unit>(code, "".toResponseBody("application/json".toMediaType()))
        )

    // --- token hygiene (regression: token retained in UI state) ------------

    @Test
    fun `token is cleared from state after successful login`() = runTest {
        val viewModel = vm(FakeAuthRepository())
        viewModel.onTokenChange("fmu1-secret-token")

        viewModel.login()
        advanceUntilIdle()

        assertEquals("", viewModel.uiState.value.token)
    }

    @Test
    fun `token is cleared from state after an auth rejection`() = runTest {
        val viewModel = vm(FakeAuthRepository(loginResult = Result.failure(httpException(401))))
        viewModel.onTokenChange("fmu1-secret-token")

        viewModel.login()
        advanceUntilIdle()

        assertEquals("", viewModel.uiState.value.token)
    }

    // A retryable failure keeps the token: the error message tells the user to
    // try again, and the field is masked, so wiping it would force a full
    // re-paste of a ~40-character secret just to press the button twice.
    @Test
    fun `token is retained after a network failure`() = runTest {
        val viewModel = vm(FakeAuthRepository(loginResult = Result.failure(IOException("offline"))))
        viewModel.onTokenChange("fmu1-secret-token")

        viewModel.login()
        advanceUntilIdle()

        assertEquals("fmu1-secret-token", viewModel.uiState.value.token)
        assertEquals(R.string.error_network, viewModel.uiState.value.errorRes)
    }

    @Test
    fun `token is retained after a server error`() = runTest {
        val viewModel = vm(FakeAuthRepository(loginResult = Result.failure(httpException(503))))
        viewModel.onTokenChange("fmu1-secret-token")

        viewModel.login()
        advanceUntilIdle()

        assertEquals("fmu1-secret-token", viewModel.uiState.value.token)
    }

    @Test
    fun `token is retained after rate limiting`() = runTest {
        val viewModel = vm(FakeAuthRepository(loginResult = Result.failure(httpException(429))))
        viewModel.onTokenChange("fmu1-secret-token")

        viewModel.login()
        advanceUntilIdle()

        assertEquals("fmu1-secret-token", viewModel.uiState.value.token)
    }

    // --- input handling ----------------------------------------------------

    @Test
    fun `whitespace is stripped from pasted token`() = runTest {
        val auth = FakeAuthRepository()
        val viewModel = vm(auth)
        viewModel.onTokenChange("  fmu1-abc\n def\t")

        viewModel.login()
        advanceUntilIdle()

        assertEquals("fmu1-abcdef", auth.lastToken)
    }

    @Test
    fun `empty token shows localized error without calling the API`() = runTest {
        val auth = FakeAuthRepository()
        val viewModel = vm(auth)
        viewModel.onTokenChange("   ")

        viewModel.login()
        advanceUntilIdle()

        assertEquals(R.string.login_error_empty_token, viewModel.uiState.value.errorRes)
        assertEquals(0, auth.loginCalls)
    }

    // --- double-tap guard --------------------------------------------------

    @Test
    fun `rapid double tap fires exactly one login request`() = runTest {
        val auth = FakeAuthRepository()
        val viewModel = vm(auth)
        viewModel.onTokenChange("fmu1-abc")

        viewModel.login()
        viewModel.login()
        advanceUntilIdle()

        assertEquals(1, auth.loginCalls)
    }

    // --- error mapping -----------------------------------------------------

    @Test
    fun `401 maps to localized auth error`() = runTest {
        val viewModel = vm(FakeAuthRepository(loginResult = Result.failure(httpException(401))))
        viewModel.onTokenChange("fmu1-bad")

        viewModel.login()
        advanceUntilIdle()

        assertEquals(R.string.error_auth, viewModel.uiState.value.errorRes)
    }

    @Test
    fun `network failure maps to localized network error`() = runTest {
        val viewModel = vm(
            FakeAuthRepository(loginResult = Result.failure(java.io.IOException("no route")))
        )
        viewModel.onTokenChange("fmu1-abc")

        viewModel.login()
        advanceUntilIdle()

        assertEquals(R.string.error_network, viewModel.uiState.value.errorRes)
    }

    // --- events ------------------------------------------------------------

    @Test
    fun `success event is buffered for collectors subscribing after emit`() = runTest {
        val viewModel = vm(FakeAuthRepository())
        viewModel.onTokenChange("fmu1-abc")

        viewModel.login()
        advanceUntilIdle() // event emitted while nobody collects (rotation window)

        // A collector attaching afterwards must still receive the event.
        var received: LoginEvent? = null
        val job = launch { received = viewModel.events.first() }
        advanceUntilIdle()
        job.cancel()

        assertEquals(LoginEvent.LoginSuccess, received)
    }

    // --- paste action (token setup wizard) ---------------------------------
    //
    // The clipboard is read only here, from an explicit user tap. Nothing in
    // these tests may become an automatic read on screen entry or resume.

    @Test
    fun `pasting stores the cleaned token and clears the previous error`() = runTest {
        val viewModel = vm(FakeAuthRepository())
        viewModel.onTokenChange("   ")
        viewModel.login() // provokes the empty-token error
        advanceUntilIdle()
        assertEquals(R.string.login_error_empty_token, viewModel.uiState.value.errorRes)

        viewModel.onTokenPasted("  fmu1-8f2c1d9e4a\n")

        assertEquals("fmu1-8f2c1d9e4a", viewModel.uiState.value.token)
        assertNull(viewModel.uiState.value.errorRes)
    }

    @Test
    fun `pasting a real token raises no warning`() = runTest {
        val viewModel = vm(FakeAuthRepository())

        viewModel.onTokenPasted("fmu1-8f2c1d9e4a")

        assertNull(viewModel.uiState.value.warningRes)
    }

    /** The case this exists for: a password pasted into the token field. */
    @Test
    fun `pasting something that is not a token raises a warning`() = runTest {
        val viewModel = vm(FakeAuthRepository())

        viewModel.onTokenPasted("Tr0ub4dor&3")

        assertNotNull(
            "a value with no fmu1- prefix must be flagged",
            viewModel.uiState.value.warningRes,
        )
    }

    @Test
    fun `the warning is not an error`() = runTest {
        val viewModel = vm(FakeAuthRepository())

        viewModel.onTokenPasted("Tr0ub4dor&3")

        assertNotNull(viewModel.uiState.value.warningRes)
        assertNull("a shape hint must not present as a failure", viewModel.uiState.value.errorRes)
    }

    @Test
    fun `the warning clears once a real token is pasted`() = runTest {
        val viewModel = vm(FakeAuthRepository())
        viewModel.onTokenPasted("Tr0ub4dor&3")
        assertNotNull(viewModel.uiState.value.warningRes)

        viewModel.onTokenPasted("fmu1-8f2c1d9e4a")

        assertNull(viewModel.uiState.value.warningRes)
    }

    /** Typing after a warned paste is the user acting on the hint. */
    @Test
    fun `the warning clears when the user edits the field`() = runTest {
        val viewModel = vm(FakeAuthRepository())
        viewModel.onTokenPasted("Tr0ub4dor&3")
        assertNotNull(viewModel.uiState.value.warningRes)

        viewModel.onTokenChange("f")

        assertNull(viewModel.uiState.value.warningRes)
    }

    /**
     * Advisory, never a gate. Fastmail owns the token format and may change
     * it; a hard block would lock every user out on the day it does.
     */
    @Test
    fun `a warned token is still submitted`() = runTest {
        val auth = FakeAuthRepository()
        val viewModel = vm(auth)
        viewModel.onTokenPasted("Tr0ub4dor&3")

        viewModel.login()
        advanceUntilIdle()

        assertEquals(1, auth.loginCalls)
        assertEquals("Tr0ub4dor&3", auth.lastToken)
        assertNotNull(viewModel.uiState.value.warningRes)
    }

    @Test
    fun `submitting a hand typed non-token warns without blocking`() = runTest {
        val auth = FakeAuthRepository()
        val viewModel = vm(auth)
        viewModel.onTokenChange("hunter2")

        viewModel.login()
        advanceUntilIdle()

        assertEquals(1, auth.loginCalls)
        assertNotNull(viewModel.uiState.value.warningRes)
    }

    /**
     * One cleaning routine, not two. A non-breaking space is the tell: any
     * second, hand-rolled implementation on the login path would keep it.
     */
    @Test
    fun `login cleans the token with the shared sanitizer`() = runTest {
        val auth = FakeAuthRepository()
        val viewModel = vm(auth)
        val pasted = "fmu1-8f2c\u00A01d9e4a"
        viewModel.onTokenChange(pasted)

        viewModel.login()
        advanceUntilIdle()

        assertEquals(TokenFormat.sanitize(pasted), auth.lastToken)
        assertEquals("fmu1-8f2c1d9e4a", auth.lastToken)
    }

    // --- paste with nothing usable in the clipboard ------------------------
    //
    // A tap that produces no text, no hint and no error is indistinguishable
    // from a broken app, on the screen where the user is already stuck.

    @Test
    fun `pasting an empty clipboard says so instead of doing nothing`() = runTest {
        val viewModel = vm(FakeAuthRepository())

        viewModel.onTokenPasted("")

        assertEquals(R.string.login_paste_empty, viewModel.uiState.value.warningRes)
    }

    /** An image or URI clip reaches the ViewModel as an empty string. */
    @Test
    fun `pasting a clipboard holding no text says so`() = runTest {
        val viewModel = vm(FakeAuthRepository())

        viewModel.onTokenPasted("")

        assertNotNull(viewModel.uiState.value.warningRes)
        assertNull("an empty clipboard is not a failure", viewModel.uiState.value.errorRes)
    }

    @Test
    fun `pasting a whitespace only clipboard says so`() = runTest {
        val viewModel = vm(FakeAuthRepository())

        viewModel.onTokenPasted("\n   \t")

        assertEquals(R.string.login_paste_empty, viewModel.uiState.value.warningRes)
    }

    /**
     * A stray newline copied off the Fastmail page used to sanitize to "" and
     * silently overwrite a token the user had typed by hand.
     */
    @Test
    fun `a whitespace only clipboard does not wipe the typed token`() = runTest {
        val viewModel = vm(FakeAuthRepository())
        viewModel.onTokenChange("fmu1-typed-by-hand")

        viewModel.onTokenPasted("\n")

        assertEquals("fmu1-typed-by-hand", viewModel.uiState.value.token)
    }

    @Test
    fun `the empty paste hint clears once something is pasted`() = runTest {
        val viewModel = vm(FakeAuthRepository())
        viewModel.onTokenPasted("")
        assertNotNull(viewModel.uiState.value.warningRes)

        viewModel.onTokenPasted("fmu1-8f2c1d9e4a")

        assertNull(viewModel.uiState.value.warningRes)
    }

    // --- oversized paste ----------------------------------------------------

    /**
     * The field is single-line and masked: every accepted character becomes a
     * glyph laid out on the main thread. A clipboard holding a copied document
     * must not reach it.
     */
    @Test
    fun `an oversized paste is capped before it reaches state`() = runTest {
        val viewModel = vm(FakeAuthRepository())

        viewModel.onTokenPasted("x".repeat(500_000))

        assertEquals(TokenFormat.MAX_PASTED_CHARS, viewModel.uiState.value.token.length)
    }

    @Test
    fun `a normal token is not truncated`() = runTest {
        val viewModel = vm(FakeAuthRepository())

        viewModel.onTokenPasted("fmu1-8f2c1d9e4a")

        assertEquals("fmu1-8f2c1d9e4a", viewModel.uiState.value.token)
    }

    // --- the shape hint follows the token it describes ----------------------

    /**
     * The hint describes what is in the field. When the field is emptied by a
     * definitive rejection, leaving it standing puts two contradictory
     * messages on screen, one of them about content that is no longer there.
     */
    @Test
    fun `the shape hint goes when an auth rejection empties the field`() = runTest {
        val viewModel = vm(FakeAuthRepository(loginResult = Result.failure(httpException(401))))
        viewModel.onTokenPasted("Tr0ub4dor&3")
        assertNotNull(viewModel.uiState.value.warningRes)

        viewModel.login()
        advanceUntilIdle()

        assertEquals("", viewModel.uiState.value.token)
        assertNull(viewModel.uiState.value.warningRes)
        assertEquals(R.string.error_auth, viewModel.uiState.value.errorRes)
    }

    @Test
    fun `the shape hint goes when the scope failure empties the field`() = runTest {
        val viewModel = vm(
            FakeAuthRepository(loginResult = Result.failure(MaskedEmailScopeMissingException()))
        )
        viewModel.onTokenPasted("Tr0ub4dor&3")
        assertNotNull(viewModel.uiState.value.warningRes)

        viewModel.login()
        advanceUntilIdle()

        assertEquals("", viewModel.uiState.value.token)
        assertNull(viewModel.uiState.value.warningRes)
    }

    /** The other half of the rule: content still there, hint still there. */
    @Test
    fun `the shape hint stays while a retryable failure keeps the token`() = runTest {
        val viewModel = vm(FakeAuthRepository(loginResult = Result.failure(IOException("offline"))))
        viewModel.onTokenPasted("Tr0ub4dor&3")

        viewModel.login()
        advanceUntilIdle()

        assertEquals("Tr0ub4dor&3", viewModel.uiState.value.token)
        assertNotNull(viewModel.uiState.value.warningRes)
    }

    // --- missing Masked Email scope ----------------------------------------

    /**
     * The scope failure must read as its own thing. "Login failed" sends the
     * user back to re-check a token that is perfectly valid — it is the
     * *permission* that is missing, and only a new token fixes it.
     */
    @Test
    fun `a token without masked email scope gets its own message`() = runTest {
        val viewModel = vm(
            FakeAuthRepository(loginResult = Result.failure(MaskedEmailScopeMissingException()))
        )
        viewModel.onTokenChange("fmu1-8f2c1d9e4a")

        viewModel.login()
        advanceUntilIdle()

        val errorRes = viewModel.uiState.value.errorRes
        assertNotNull(errorRes)
        assertNotEquals(R.string.login_error_failed, errorRes)
        // Nor any other message already in circulation.
        assertNotEquals(R.string.login_error_empty_token, errorRes)
        assertNotEquals(R.string.error_auth, errorRes)
        assertNotEquals(R.string.error_network, errorRes)
        assertNotEquals(R.string.error_server, errorRes)
        assertNotEquals(R.string.error_rate_limit, errorRes)
    }

    /** Retrying the same token cannot help, so the secret is dropped. */
    @Test
    fun `a token without masked email scope is cleared from state`() = runTest {
        val viewModel = vm(
            FakeAuthRepository(loginResult = Result.failure(MaskedEmailScopeMissingException()))
        )
        viewModel.onTokenChange("fmu1-8f2c1d9e4a")

        viewModel.login()
        advanceUntilIdle()

        assertEquals("", viewModel.uiState.value.token)
        assertEquals(false, viewModel.uiState.value.isLoading)
    }

    // --- demo exit ---------------------------------------------------------

    @Test
    fun `the demo exit enters demo mode and navigates`() = runTest {
        val demo = FakeDemoModeActivator()
        val viewModel = vm(FakeAuthRepository(), demo)

        viewModel.enterDemoMode()
        var received: LoginEvent? = null
        val job = launch { received = viewModel.events.first() }
        advanceUntilIdle()
        job.cancel()

        assertEquals(1, demo.activateCalls)
        assertEquals(LoginEvent.EnterDemo, received)
    }

    /**
     * Mirrors WelcomeViewModel: a failed DataStore write must not crash, and
     * must not navigate into a demo whose mode flag was never persisted.
     */
    @Test
    fun `a failed demo activation neither navigates nor crashes`() = runTest {
        val demo = FakeDemoModeActivator(failure = IllegalStateException("disk full"))
        val viewModel = vm(FakeAuthRepository(), demo)

        viewModel.enterDemoMode()
        var received: LoginEvent? = null
        val job = launch { received = viewModel.events.first() }
        advanceUntilIdle()
        job.cancel()

        assertEquals(1, demo.activateCalls)
        assertNull(received)
    }

    @Test
    fun `the demo exit does not attempt a login`() = runTest {
        val auth = FakeAuthRepository()
        val viewModel = vm(auth)

        viewModel.enterDemoMode()
        advanceUntilIdle()

        assertEquals(0, auth.loginCalls)
    }

    /**
     * Leaving for the demo is a terminal transition like any other, so it drops
     * the secret with it. The user taking this exit is the one who could not
     * get a token to work — i.e. the likeliest to have their Fastmail
     * *password* sitting in that field.
     */
    @Test
    fun `the demo exit clears the token from state`() = runTest {
        val viewModel = vm(FakeAuthRepository())
        viewModel.onTokenPasted("Tr0ub4dor&3")

        viewModel.enterDemoMode()
        advanceUntilIdle()

        assertEquals("", viewModel.uiState.value.token)
        assertNull(viewModel.uiState.value.warningRes)
    }

    /** A write that never landed must leave the user's input to retry with. */
    @Test
    fun `a failed demo activation keeps the token`() = runTest {
        val demo = FakeDemoModeActivator(failure = IllegalStateException("disk full"))
        val viewModel = vm(FakeAuthRepository(), demo)
        viewModel.onTokenChange("fmu1-8f2c1d9e4a")

        viewModel.enterDemoMode()
        advanceUntilIdle()

        assertEquals("fmu1-8f2c1d9e4a", viewModel.uiState.value.token)
    }

    // --- demo and login are mutually exclusive ------------------------------

    /**
     * Both buttons are gated on isLoading. Without the flag being raised
     * synchronously, a demo tap followed by an Unlock tap in the same frame
     * persists a real token into a session that is about to render demo data.
     */
    @Test
    fun `a login tapped in the same frame as the demo exit is ignored`() = runTest {
        val auth = FakeAuthRepository()
        val viewModel = vm(auth)
        viewModel.onTokenChange("fmu1-8f2c1d9e4a")

        viewModel.enterDemoMode()
        viewModel.login()
        advanceUntilIdle()

        assertEquals(0, auth.loginCalls)
    }

    @Test
    fun `a second demo tap does not activate twice`() = runTest {
        val demo = FakeDemoModeActivator()
        val viewModel = vm(FakeAuthRepository(), demo)

        viewModel.enterDemoMode()
        viewModel.enterDemoMode()
        advanceUntilIdle()

        assertEquals(1, demo.activateCalls)
    }

    /** A failed activation must not leave both buttons disabled for good. */
    @Test
    fun `a failed demo activation releases the buttons`() = runTest {
        val demo = FakeDemoModeActivator(failure = IllegalStateException("disk full"))
        val viewModel = vm(FakeAuthRepository(), demo)

        viewModel.enterDemoMode()
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.isLoading)
    }

    @Test
    fun `a successful demo activation releases the buttons`() = runTest {
        val viewModel = vm(FakeAuthRepository())

        viewModel.enterDemoMode()
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.isLoading)
    }

    // --- clipboard token detection (UX Recommendation B1) ------------------

    @Test
    fun `checking clipboard with a valid token shape detects it without auto filling`() = runTest {
        val viewModel = vm(FakeAuthRepository())

        viewModel.onCheckClipboardForToken("fmu1-8f2c1d9e4a")

        assertEquals("fmu1-8f2c1d9e4a", viewModel.uiState.value.detectedClipboardToken)
        assertEquals("", viewModel.uiState.value.token)
    }

    @Test
    fun `checking clipboard with non token content does not set detected token`() = runTest {
        val viewModel = vm(FakeAuthRepository())

        viewModel.onCheckClipboardForToken("some random text")

        assertNull(viewModel.uiState.value.detectedClipboardToken)
    }

    @Test
    fun `checking clipboard when token matches current input does not set detected token`() = runTest {
        val viewModel = vm(FakeAuthRepository())
        viewModel.onTokenChange("fmu1-8f2c1d9e4a")

        viewModel.onCheckClipboardForToken("fmu1-8f2c1d9e4a")

        assertNull(viewModel.uiState.value.detectedClipboardToken)
    }

    @Test
    fun `confirming detected clipboard token populates field and clears prompt`() = runTest {
        val viewModel = vm(FakeAuthRepository())
        viewModel.onCheckClipboardForToken("  fmu1-8f2c1d9e4a\n")
        assertEquals("fmu1-8f2c1d9e4a", viewModel.uiState.value.detectedClipboardToken)

        viewModel.onUseDetectedClipboardToken()

        assertEquals("fmu1-8f2c1d9e4a", viewModel.uiState.value.token)
        assertNull(viewModel.uiState.value.detectedClipboardToken)
    }

    @Test
    fun `dismissing detected clipboard token clears prompt without changing field`() = runTest {
        val viewModel = vm(FakeAuthRepository())
        viewModel.onCheckClipboardForToken("fmu1-8f2c1d9e4a")

        viewModel.onDismissDetectedClipboardToken()

        assertNull(viewModel.uiState.value.detectedClipboardToken)
        assertEquals("", viewModel.uiState.value.token)
    }
}
