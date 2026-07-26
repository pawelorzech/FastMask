package com.fastmask.ui.settings

import com.fastmask.R
import com.fastmask.data.local.SettingsDataStore
import com.fastmask.domain.analytics.MonetizationEvent
import com.fastmask.domain.crash.CrashReportingController
import com.fastmask.domain.model.Accent
import com.fastmask.domain.model.AppMode
import com.fastmask.domain.model.ProStatus
import com.fastmask.domain.usecase.ExportMasksUseCase
import com.fastmask.domain.usecase.GetCurrentLanguageUseCase
import com.fastmask.domain.usecase.LogoutUseCase
import com.fastmask.domain.usecase.SetLanguageUseCase
import com.fastmask.testutil.FakeCrashReporter
import com.fastmask.testutil.FakeMaskedEmailRepository
import com.fastmask.testutil.FakeMonetizationAnalytics
import com.fastmask.testutil.FakeProRepository
import com.fastmask.testutil.MainDispatcherRule
import com.fastmask.testutil.mask
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val proRepository = FakeProRepository()
    private val analytics = FakeMonetizationAnalytics()
    private val maskRepository = FakeMaskedEmailRepository()

    private val crashReporter = FakeCrashReporter()

    /** Stored crash-reporting preference; opt-out, so it starts on. */
    private val storedCrashReporting = MutableStateFlow(true)

    private val settingsDataStore = mockk<SettingsDataStore> {
        every { appMode } returns flowOf(AppMode.REAL)
        every { appModeBlocking() } returns AppMode.REAL
        every { accent } returns flowOf(Accent.AMBER)
        every { appLockEnabled } returns flowOf(false)
        every { crashReportingEnabled } returns storedCrashReporting
        every { crashReportingEnabledBlocking() } answers { storedCrashReporting.value }
        coJustRun { setAccent(any()) }
        coJustRun { setAppLockEnabled(any()) }
        coJustRun { setCrashReportingEnabled(any()) }
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
            crashReporting = CrashReportingController(
                reporter = crashReporter,
                isDebugBuild = false,
            ),
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
    fun `failed export surfaces an error event with the generic reason`() = runTest {
        proRepository.statusFlow.value = ProStatus.PRO
        maskRepository.failure = RuntimeException("boom")
        val vm = viewModel()
        vm.onExportClick()
        advanceUntilIdle()

        assertEquals(
            SettingsEvent.ExportFailed(R.string.settings_export_failed),
            vm.events.first(),
        )
    }

    // The export fetches every mask over the network first, so it must name the
    // same causes the rest of the app names — "Export failed. Try again." on an
    // offline device told the user to do the one thing that cannot work.
    @Test
    fun `export failure reports the network cause`() = runTest {
        proRepository.statusFlow.value = ProStatus.PRO
        maskRepository.failure = IOException("offline")
        val vm = viewModel()
        vm.onExportClick()
        advanceUntilIdle()

        assertEquals(SettingsEvent.ExportFailed(R.string.error_network), vm.events.first())
    }

    @Test
    fun `export failure reports rate limiting`() = runTest {
        proRepository.statusFlow.value = ProStatus.PRO
        maskRepository.failure = HttpException(
            Response.error<Unit>(429, "".toResponseBody("application/json".toMediaType()))
        )
        val vm = viewModel()
        vm.onExportClick()
        advanceUntilIdle()

        assertEquals(SettingsEvent.ExportFailed(R.string.error_rate_limit), vm.events.first())
    }

    // --- Crash reporting (opt-out) ---

    /**
     * The switch is rendered before the asynchronous flow has emitted, so its
     * initial value is seeded synchronously from storage — the same way
     * `appMode` is. A plain `DEFAULT_ENABLED` seed painted the switch ON for
     * the first frames of every entry into Settings, showing a user who had
     * opted out the opposite of their own choice.
     */
    @Test
    fun `the crash reporting switch shows a stored opt-out from the very first frame`() = runTest {
        storedCrashReporting.value = false

        val vm = viewModel()

        assertFalse(
            "the switch must never flash ON for someone who opted out",
            vm.crashReportingEnabled.value,
        )
    }

    /**
     * The other direction, which the seed must not break: an install that never
     * touched the switch is opted in, and showing "off" would claim nothing is
     * collected when it is.
     */
    @Test
    fun `the crash reporting switch reads as on for an untouched install`() = runTest {
        storedCrashReporting.value = true

        val vm = viewModel()

        assertTrue(vm.crashReportingEnabled.value)
    }

    @Test
    fun `the crash reporting switch follows the stored preference in both directions`() = runTest {
        val vm = viewModel()
        val collector = launch { vm.crashReportingEnabled.collect { } }
        advanceUntilIdle()

        assertTrue("an untouched install is opted in", vm.crashReportingEnabled.value)

        storedCrashReporting.value = false
        advanceUntilIdle()
        assertFalse("an opt-out must show as off", vm.crashReportingEnabled.value)

        storedCrashReporting.value = true
        advanceUntilIdle()
        assertTrue("opting back in must show as on", vm.crashReportingEnabled.value)

        collector.cancel()
    }

    @Test
    fun `opting out persists the choice and stops collection immediately`() = runTest {
        val vm = viewModel()

        vm.onCrashReportingToggled(false)
        advanceUntilIdle()

        coVerify { settingsDataStore.setCrashReportingEnabled(false) }
        assertEquals(listOf("collection=false", "delete"), crashReporter.calls)
    }

    @Test
    fun `opting back in persists the choice and resumes collection without purging`() = runTest {
        val vm = viewModel()

        vm.onCrashReportingToggled(true)
        advanceUntilIdle()

        coVerify { settingsDataStore.setCrashReportingEnabled(true) }
        assertEquals(listOf("collection=true"), crashReporter.calls)
    }

    /**
     * A full disk must not turn an opt-out into a crash, and must not turn it
     * into a no-op either: the user asked to stop being reported on, so
     * collection stops for this session even though the choice did not survive.
     */
    @Test
    fun `a failed write still stops collection and does not crash`() = runTest {
        coEvery { settingsDataStore.setCrashReportingEnabled(any()) } throws
            IOException("No space left on device")
        val vm = viewModel()

        vm.onCrashReportingToggled(false)
        advanceUntilIdle()

        assertEquals(listOf("collection=false", "delete"), crashReporter.calls)
    }

    /**
     * The SDK call happens on the main thread, straight off a tap, and it is
     * where Firebase initialises itself — so on a device where the default
     * `FirebaseApp` never came up it throws. Unwrapped, that took the Settings
     * screen down. The choice must still be persisted, so the next launch
     * re-applies it.
     */
    @Test
    fun `an sdk that cannot start does not crash the settings screen`() = runTest {
        crashReporter.failure = IllegalStateException(
            "Default FirebaseApp is not initialized in this process"
        )
        val vm = viewModel()

        vm.onCrashReportingToggled(false)
        advanceUntilIdle()

        coVerify { settingsDataStore.setCrashReportingEnabled(false) }
    }

    @Test
    fun `toggling the same value twice keeps the stored state consistent`() = runTest {
        val vm = viewModel()

        vm.onCrashReportingToggled(false)
        advanceUntilIdle()
        vm.onCrashReportingToggled(false)
        advanceUntilIdle()

        coVerify(exactly = 2) { settingsDataStore.setCrashReportingEnabled(false) }
        coVerify(exactly = 0) { settingsDataStore.setCrashReportingEnabled(true) }
    }
}
