package com.fastmask.ui.settings

import android.content.Context
import com.fastmask.R
import com.fastmask.data.local.AppLockStore
import com.fastmask.data.local.ExportCache
import com.fastmask.data.local.LanguageSettings
import com.fastmask.data.local.SettingsDataStore
import com.fastmask.domain.analytics.MonetizationEvent
import com.fastmask.data.crash.CrashReporting
import com.fastmask.domain.model.Accent
import com.fastmask.domain.model.ProStatus
import com.fastmask.testutil.FakeSessionRepository
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import com.fastmask.testutil.FakeCrashReporter
import com.fastmask.testutil.FakePreferencesDataStore
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import com.fastmask.testutil.failureForStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val proRepository = FakeProRepository()
    private val analytics = FakeMonetizationAnalytics()
    private val maskRepository = FakeMaskedEmailRepository()

    private val crashReporter = FakeCrashReporter()

    /**
     * A real [ExportCache] over a temp directory rather than a mock: the export
     * path's whole point is that a file lands on disk, and the sign-out guard is
     * state inside this class.
     */
    @get:Rule
    val temp = TemporaryFolder()

    private val exportCache: ExportCache by lazy {
        ExportCache(
            mockk<Context> { every { cacheDir } returns temp.newFolder("cache") },
            mainDispatcherRule.dispatcher,
        )
    }

    private val crashKey = booleanPreferencesKey("crash_reporting_enabled")

    /** Backing store of the crash reporting preference; empty means opted in. */
    private var crashStore = FakePreferencesDataStore()

    private val settingsDataStore = mockk<SettingsDataStore> {
        every { accent } returns flowOf(Accent.AMBER)
        coJustRun { setAccent(any()) }
    }

    private val appLockStore = mockk<AppLockStore> {
        every { appLockEnabled } returns flowOf(false)
        coJustRun { setAppLockEnabled(any()) }
    }

    private fun viewModel(): SettingsViewModel {
        val languageSettings = mockk<LanguageSettings>(relaxed = true) {
            every { language } returns flowOf(null)
        }
        return SettingsViewModel(
            languageSettings = languageSettings,
            sessionRepository = FakeSessionRepository(),
            settingsDataStore = settingsDataStore,
            appLockStore = appLockStore,
            proRepository = proRepository,
            maskedEmailRepository = maskRepository,
            exportCache = exportCache,
            analytics = analytics,
            crashReporting = CrashReporting(
                dataStore = crashStore,
                reporter = crashReporter,
                isDebugBuild = false,
            ),
        )
    }

    private fun SettingsViewModel.requestAndConfirmExport() {
        onExportClick()
        onExportConfirmed()
    }

    private fun hasCsvFile(): Boolean =
        temp.root.walkTopDown().any { it.isFile && it.extension == "csv" }

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
    fun `selecting an accent with pro persists it`() = runTest {
        proRepository.statusFlow.value = ProStatus.PRO
        val vm = viewModel()
        vm.onAccentSelected(Accent.SAGE)
        advanceUntilIdle()

        coVerify { settingsDataStore.setAccent(Accent.SAGE) }
    }

    // Wallpaper colors are free for everyone (the picker shows it unlocked), so
    // choosing it must persist without Pro — e.g. after a lapsed Pro stored a seed.
    @Test
    fun `selecting the wallpaper accent without pro persists it`() = runTest {
        val vm = viewModel()
        vm.onAccentSelected(Accent.DYNAMIC)
        advanceUntilIdle()

        coVerify { settingsDataStore.setAccent(Accent.DYNAMIC) }
    }

    @Test
    fun `selecting a seed accent without pro persists nothing`() = runTest {
        val vm = viewModel()
        vm.onAccentSelected(Accent.SAGE)
        advanceUntilIdle()

        coVerify(exactly = 0) { settingsDataStore.setAccent(any()) }
    }

    @Test
    fun `enabling app lock without pro routes to paywall and does not persist`() = runTest {
        val vm = viewModel()
        vm.onAppLockToggled(true)
        advanceUntilIdle()

        assertEquals(SettingsEvent.OpenPro("app_lock"), vm.events.first())
        coVerify(exactly = 0) { appLockStore.setAppLockEnabled(any()) }
    }

    @Test
    fun `disabling app lock always works even without pro`() = runTest {
        val vm = viewModel()
        vm.onAppLockToggled(false)
        advanceUntilIdle()

        coVerify { appLockStore.setAppLockEnabled(false) }
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
    fun `export with pro waits for explicit confirmation before reading or writing data`() = runTest {
        proRepository.statusFlow.value = ProStatus.PRO
        val vm = viewModel()

        vm.onExportClick()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.showExportConfirmation)
        assertEquals(0, maskRepository.getCalls)
        assertFalse(hasCsvFile())
    }

    @Test
    fun `dismissing export confirmation creates no plaintext file`() = runTest {
        proRepository.statusFlow.value = ProStatus.PRO
        val vm = viewModel()

        vm.onExportClick()
        vm.onExportConfirmationDismissed()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.showExportConfirmation)
        assertEquals(0, maskRepository.getCalls)
        assertFalse(hasCsvFile())
    }

    @Test
    fun `losing pro while export confirmation is open rechecks the gate`() = runTest {
        proRepository.statusFlow.value = ProStatus.PRO
        val vm = viewModel()
        vm.onExportClick()
        proRepository.statusFlow.value = ProStatus.FREE

        vm.onExportConfirmed()
        advanceUntilIdle()

        assertEquals(SettingsEvent.OpenPro("export"), vm.events.first())
        assertEquals(0, maskRepository.getCalls)
        assertFalse(hasCsvFile())
    }

    @Test
    fun `confirming export twice starts only one export`() = runTest {
        proRepository.statusFlow.value = ProStatus.PRO
        maskRepository.emails = listOf(mask("one"))
        val vm = viewModel()
        vm.onExportClick()

        vm.onExportConfirmed()
        vm.onExportConfirmed()
        advanceUntilIdle()

        assertEquals(1, maskRepository.getCalls)
        assertTrue(vm.events.first() is SettingsEvent.ShareCsv)
    }

    @Test
    fun `export with pro shares a csv containing every mask`() = runTest {
        proRepository.statusFlow.value = ProStatus.PRO
        maskRepository.emails = listOf(mask("one"), mask("two"))
        val vm = viewModel()
        vm.requestAndConfirmExport()
        advanceUntilIdle()

        val event = vm.events.first()
        assertTrue(event is SettingsEvent.ShareCsv)
        assertEquals(1, maskRepository.getCalls)
        // The event now carries the written file, not the string: producing it
        // belongs to the ViewModel, which holds the ExportCache and can abandon
        // an export whose fetch outlived the session.
        val csv = (event as SettingsEvent.ShareCsv).file.readText()
        assertTrue(csv.contains("one@fastmail.com"))
        assertTrue(csv.contains("two@fastmail.com"))
    }

    // The CSV is the only copy of the account's masks on the device that is not
    // Keystore-encrypted, so an export whose network fetch outlived a sign-out
    // must never reach the disk. Nothing to age out an hour later.
    @Test
    fun `an export whose fetch outlived a sign-out is abandoned, not written`() = runTest {
        proRepository.statusFlow.value = ProStatus.PRO
        maskRepository.emails = listOf(mask("one"))
        // The sign-out lands while the fetch is still in flight — after the
        // ViewModel captured the generation, before the CSV is written.
        maskRepository.beforeGet = { exportCache.clear() }
        val vm = viewModel()

        vm.requestAndConfirmExport()
        advanceUntilIdle()

        assertEquals(
            SettingsEvent.ExportFailed(R.string.settings_export_failed_write),
            vm.events.first(),
        )
    }

    @Test
    fun `failed export surfaces an error event with the generic reason`() = runTest {
        proRepository.statusFlow.value = ProStatus.PRO
        maskRepository.failure = RuntimeException("boom")
        val vm = viewModel()
        vm.requestAndConfirmExport()
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
        vm.requestAndConfirmExport()
        advanceUntilIdle()

        assertEquals(SettingsEvent.ExportFailed(R.string.error_network), vm.events.first())
    }

    @Test
    fun `export failure reports rate limiting`() = runTest {
        proRepository.statusFlow.value = ProStatus.PRO
        maskRepository.failure = failureForStatus(429)
        val vm = viewModel()
        vm.requestAndConfirmExport()
        advanceUntilIdle()

        assertEquals(SettingsEvent.ExportFailed(R.string.error_rate_limit), vm.events.first())
    }

    // --- Crash reporting (opt-out) ---
    // The preference / policy / ordering rules are covered in CrashReportingTest;
    // these cover what the Settings screen adds on top.

    /**
     * The switch is rendered before the asynchronous flow has emitted, so its
     * initial value is seeded synchronously from storage — the same way
     * `appMode` is. A plain default painted the switch ON for the first frames
     * of every entry into Settings, showing a user who had opted out the
     * opposite of their own choice.
     */
    @Test
    fun `the crash reporting switch shows a stored opt-out from the very first frame`() = runTest {
        crashStore = FakePreferencesDataStore(preferencesOf(crashKey to false))

        val vm = viewModel()

        assertFalse(
            "the switch must never flash ON for someone who opted out",
            vm.crashReportingEnabled.value,
        )
    }

    @Test
    fun `the crash reporting switch follows the stored preference in both directions`() = runTest {
        val vm = viewModel()
        val collector = launch { vm.crashReportingEnabled.collect { } }
        advanceUntilIdle()

        assertTrue("an untouched install is opted in", vm.crashReportingEnabled.value)

        crashStore.updateData { it.toMutablePreferences().apply { set(crashKey, false) } }
        advanceUntilIdle()
        assertFalse("an opt-out must show as off", vm.crashReportingEnabled.value)

        crashStore.updateData { it.toMutablePreferences().apply { set(crashKey, true) } }
        advanceUntilIdle()
        assertTrue("opting back in must show as on", vm.crashReportingEnabled.value)

        collector.cancel()
    }

    @Test
    fun `opting out persists the choice and stops collection`() = runTest {
        val vm = viewModel()

        vm.onCrashReportingToggled(false)
        advanceUntilIdle()

        assertEquals(false, crashStore.snapshot()[crashKey])
        assertEquals(listOf("collection=false", "delete"), crashReporter.calls)
    }

    /** A full disk must not turn an opt-out into a crash, nor into a no-op. */
    @Test
    fun `a failed write still stops collection and does not crash`() = runTest {
        crashStore.writeFailure = IOException("No space left on device")
        val vm = viewModel()

        vm.onCrashReportingToggled(false)
        advanceUntilIdle()

        assertEquals(listOf("collection=false", "delete"), crashReporter.calls)
    }
}

