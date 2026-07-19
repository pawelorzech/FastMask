package com.fastmask.ui.settings

import com.fastmask.data.local.SettingsDataStore
import com.fastmask.domain.analytics.MonetizationEvent
import com.fastmask.domain.model.Accent
import com.fastmask.domain.model.AppMode
import com.fastmask.domain.model.ProStatus
import com.fastmask.domain.usecase.ExportMasksUseCase
import com.fastmask.domain.usecase.GetCurrentLanguageUseCase
import com.fastmask.domain.usecase.LogoutUseCase
import com.fastmask.domain.usecase.SetLanguageUseCase
import com.fastmask.testutil.FakeMaskedEmailRepository
import com.fastmask.testutil.FakeMonetizationAnalytics
import com.fastmask.testutil.FakeProRepository
import com.fastmask.testutil.MainDispatcherRule
import com.fastmask.testutil.mask
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val proRepository = FakeProRepository()
    private val analytics = FakeMonetizationAnalytics()
    private val maskRepository = FakeMaskedEmailRepository()

    private val settingsDataStore = mockk<SettingsDataStore> {
        every { appMode } returns flowOf(AppMode.REAL)
        every { appModeBlocking() } returns AppMode.REAL
        every { accent } returns flowOf(Accent.AMBER)
        every { appLockEnabled } returns flowOf(false)
        coJustRun { setAccent(any()) }
        coJustRun { setAppLockEnabled(any()) }
    }

    private fun viewModel(): SettingsViewModel {
        val getLanguage = mockk<GetCurrentLanguageUseCase> {
            every { this@mockk.invoke() } returns flowOf(null)
        }
        return SettingsViewModel(
            getCurrentLanguageUseCase = getLanguage,
            setLanguageUseCase = mockk<SetLanguageUseCase>(relaxed = true),
            logoutUseCase = mockk<LogoutUseCase>(relaxed = true),
            settingsDataStore = settingsDataStore,
            proRepository = proRepository,
            exportMasksUseCase = ExportMasksUseCase(maskRepository),
            analytics = analytics,
        )
    }

    @Test
    fun `accent tap without pro routes to paywall and tracks the gate`() = runTest {
        val vm = viewModel()
        vm.onAccentClick()
        advanceUntilIdle()

        val event = vm.events.first()
        assertEquals(SettingsEvent.OpenPro("accent"), event)
        assertTrue(analytics.events().contains(MonetizationEvent.PREMIUM_FEATURE_TAPPED))
    }

    @Test
    fun `accent tap with pro opens the picker dialog`() = runTest {
        proRepository.statusFlow.value = ProStatus.PRO
        val vm = viewModel()
        vm.onAccentClick()

        assertTrue(vm.uiState.value.showAccentDialog)
    }

    @Test
    fun `selecting an accent with pro persists it`() = runTest {
        proRepository.statusFlow.value = ProStatus.PRO
        val vm = viewModel()
        vm.onAccentSelected(Accent.SAGE)
        advanceUntilIdle()

        coVerify { settingsDataStore.setAccent(Accent.SAGE) }
    }

    @Test
    fun `enabling app lock without pro routes to paywall and does not persist`() = runTest {
        val vm = viewModel()
        vm.onAppLockToggled(true)
        advanceUntilIdle()

        assertEquals(SettingsEvent.OpenPro("app_lock"), vm.events.first())
        coVerify(exactly = 0) { settingsDataStore.setAppLockEnabled(any()) }
    }

    @Test
    fun `disabling app lock always works even without pro`() = runTest {
        val vm = viewModel()
        vm.onAppLockToggled(false)
        advanceUntilIdle()

        coVerify { settingsDataStore.setAppLockEnabled(false) }
    }

    @Test
    fun `export without pro routes to paywall`() = runTest {
        val vm = viewModel()
        vm.onExportClick()
        advanceUntilIdle()

        assertEquals(SettingsEvent.OpenPro("export"), vm.events.first())
        assertEquals(0, maskRepository.getCalls)
    }

    @Test
    fun `export with pro shares a csv containing every mask`() = runTest {
        proRepository.statusFlow.value = ProStatus.PRO
        maskRepository.emails = listOf(mask("one"), mask("two"))
        val vm = viewModel()
        vm.onExportClick()
        advanceUntilIdle()

        val event = vm.events.first()
        assertTrue(event is SettingsEvent.ShareCsv)
        val csv = (event as SettingsEvent.ShareCsv).csv
        assertTrue(csv.contains("one@fastmail.com"))
        assertTrue(csv.contains("two@fastmail.com"))
    }

    @Test
    fun `failed export surfaces an error event`() = runTest {
        proRepository.statusFlow.value = ProStatus.PRO
        maskRepository.failure = RuntimeException("boom")
        val vm = viewModel()
        vm.onExportClick()
        advanceUntilIdle()

        assertEquals(SettingsEvent.ExportFailed, vm.events.first())
    }
}
