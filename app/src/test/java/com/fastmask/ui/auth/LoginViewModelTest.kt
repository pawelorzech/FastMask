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
}
