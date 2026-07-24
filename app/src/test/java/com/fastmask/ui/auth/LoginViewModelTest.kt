@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.fastmask.ui.auth

import com.fastmask.R
import com.fastmask.domain.usecase.LoginUseCase
import com.fastmask.testutil.FakeAuthRepository
import com.fastmask.testutil.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class LoginViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun vm(auth: FakeAuthRepository) = LoginViewModel(LoginUseCase(auth))

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
}
