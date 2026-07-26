@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.fastmask.ui.welcome

import com.fastmask.domain.usecase.LoginUseCase
import com.fastmask.testutil.FakeAuthRepository
import com.fastmask.testutil.FakeDemoModeActivator
import com.fastmask.testutil.MainDispatcherRule
import com.fastmask.ui.auth.LoginViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * Welcome is the older of the two demo entry points. These tests pin the
 * contract it now shares with the login screen: the demo sequence itself lives
 * behind `DemoModeActivator`, and both screens run that one mechanism.
 */
class WelcomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `entering demo runs the shared activation and navigates`() = runTest {
        val demo = FakeDemoModeActivator()
        val viewModel = WelcomeViewModel(demo)

        viewModel.enterDemoMode()
        var received: WelcomeEvent? = null
        val job = launch { received = viewModel.events.first() }
        advanceUntilIdle()
        job.cancel()

        assertEquals(1, demo.activateCalls)
        assertEquals(WelcomeEvent.EnterDemo, received)
    }

    @Test
    fun `a failed activation neither navigates nor crashes`() = runTest {
        val demo = FakeDemoModeActivator(failure = IllegalStateException("disk full"))
        val viewModel = WelcomeViewModel(demo)

        viewModel.enterDemoMode()
        var received: WelcomeEvent? = null
        val job = launch { received = viewModel.events.first() }
        advanceUntilIdle()
        job.cancel()

        assertEquals(1, demo.activateCalls)
        assertNull(received)
    }

    @Test
    fun `sign in emits the navigation event`() = runTest {
        val viewModel = WelcomeViewModel(FakeDemoModeActivator())

        viewModel.goToSignIn()
        var received: WelcomeEvent? = null
        val job = launch { received = viewModel.events.first() }
        advanceUntilIdle()
        job.cancel()

        assertEquals(WelcomeEvent.GoToSignIn, received)
    }

    /**
     * The anti-duplication check. Adding a demo exit to the login screen is an
     * invitation to re-type "reset, set DEMO, clear tutorial" a second time —
     * at which point the two paths drift. Both entry points must go through
     * the same collaborator, so one activator sees both taps.
     */
    @Test
    fun `welcome and login enter demo through the same mechanism`() = runTest {
        val demo = FakeDemoModeActivator()

        WelcomeViewModel(demo).enterDemoMode()
        LoginViewModel(LoginUseCase(FakeAuthRepository()), demo).enterDemoMode()
        advanceUntilIdle()

        assertEquals(2, demo.activateCalls)
    }
}
