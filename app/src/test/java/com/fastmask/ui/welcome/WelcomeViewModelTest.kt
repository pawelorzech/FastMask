@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.fastmask.ui.welcome

import com.fastmask.R
import com.fastmask.testutil.FakeSessionRepository
import com.fastmask.testutil.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class WelcomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `entering demo runs the shared activation and navigates`() = runTest {
        val demo = FakeSessionRepository()
        val viewModel = WelcomeViewModel(demo)

        viewModel.enterDemoMode()
        var received: WelcomeEvent? = null
        val job = launch { received = viewModel.events.first() }
        advanceUntilIdle()
        job.cancel()

        assertEquals(1, demo.enterDemoCalls)
        assertEquals(WelcomeEvent.EnterDemo, received)
    }

    @Test
    fun `a failed activation neither navigates nor crashes`() = runTest {
        val demo = FakeSessionRepository(enterDemoFailure = IllegalStateException("disk full"))
        val viewModel = WelcomeViewModel(demo)

        viewModel.enterDemoMode()
        var received: WelcomeEvent? = null
        val job = launch { received = viewModel.events.first() }
        advanceUntilIdle()
        job.cancel()

        assertEquals(1, demo.enterDemoCalls)
        assertNull(received)
        assertEquals(R.string.demo_error_start, viewModel.uiState.value.errorRes)
        assertEquals(false, viewModel.uiState.value.isLoading)
    }

    @Test
    fun `rapid demo taps activate once and expose immediate progress`() = runTest {
        val demo = FakeSessionRepository()
        val viewModel = WelcomeViewModel(demo)

        viewModel.enterDemoMode()
        assertEquals(true, viewModel.uiState.value.isLoading)
        viewModel.enterDemoMode()
        advanceUntilIdle()

        assertEquals(1, demo.enterDemoCalls)
        assertEquals(false, viewModel.uiState.value.isLoading)
    }
}
