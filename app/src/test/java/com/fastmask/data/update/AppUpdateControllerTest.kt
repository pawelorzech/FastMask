package com.fastmask.data.update

import android.app.Activity
import android.content.Context
import android.content.pm.InstallSourceInfo
import android.content.pm.PackageManager
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.Task
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Behaviour of the one class that talks to Play's in-app update API, driven through a mocked
 * [AppUpdateManager]. Covers the properties that used to be pinned by reading the controller's
 * source text: the install-source gate, a failed launch not spending the offer, and only a user
 * cancellation silencing a release. The controller takes no Pro or reporting collaborator, so
 * "updating is free and reports nothing" is a property of its constructor, not of a grep.
 */
class AppUpdateControllerTest {

    private val activity = mockk<Activity>(relaxed = true)
    private val launcher = mockk<ActivityResultLauncher<IntentSenderRequest>>(relaxed = true)

    private var dismissed = AppUpdatePolicy.NO_VERSION
    private val preferences = mockk<AppUpdatePreferences> {
        every { dismissedVersionCode } answers { dismissed }
        every { dismissedVersionCode = any() } answers { dismissed = firstArg() }
    }

    private val manager = mockk<AppUpdateManager>(relaxed = true)

    private fun controller(installer: String? = "com.android.vending"): AppUpdateController {
        val installSource = mockk<InstallSourceInfo> { every { installingPackageName } returns installer }
        val packages = mockk<PackageManager> {
            every { getInstallSourceInfo(any()) } returns installSource
            @Suppress("DEPRECATION")
            every { getInstallerPackageName(any()) } returns installer
        }
        val context = mockk<Context> {
            every { packageName } returns "com.fastmask"
            every { packageManager } returns packages
        }
        return AppUpdateController(context, preferences, dagger.Lazy { manager })
    }

    private fun offer(
        versionCode: Int = 50,
        priority: Int = 0,
        immediateAllowed: Boolean = false,
        downloaded: Boolean = false,
    ) {
        val info = mockk<AppUpdateInfo> {
            every { updateAvailability() } returns UpdateAvailability.UPDATE_AVAILABLE
            every { availableVersionCode() } returns versionCode
            every { isUpdateTypeAllowed(com.google.android.play.core.install.model.AppUpdateType.FLEXIBLE) } returns true
            every { isUpdateTypeAllowed(com.google.android.play.core.install.model.AppUpdateType.IMMEDIATE) } returns immediateAllowed
            every { updatePriority() } returns priority
            every { clientVersionStalenessDays() } returns 0
            every { installStatus() } returns if (downloaded) InstallStatus.DOWNLOADED else InstallStatus.UNKNOWN
        }
        val task = mockk<Task<AppUpdateInfo>> {
            every { isSuccessful } returns true
            every { result } returns info
            every { addOnCompleteListener(any<OnCompleteListener<AppUpdateInfo>>()) } answers {
                firstArg<OnCompleteListener<AppUpdateInfo>>().onComplete(this@mockk)
                this@mockk
            }
        }
        every { manager.appUpdateInfo } returns task
    }

    private fun flowStarts(started: Boolean) {
        every {
            manager.startUpdateFlowForResult(any(), any<ActivityResultLauncher<IntentSenderRequest>>(), any<AppUpdateOptions>())
        } returns started
    }

    private fun verifyFlowStarted(times: Int) = verify(exactly = times) {
        manager.startUpdateFlowForResult(any(), any<ActivityResultLauncher<IntentSenderRequest>>(), any<AppUpdateOptions>())
    }

    @Test
    fun `an app not installed by Play never asks Play for an update`() = runTest {
        offer()
        flowStarts(true)

        controller(installer = null).checkAndStart(activity, launcher)

        verifyFlowStarted(0)
    }

    @Test
    fun `a Play install is offered a flexible update exactly once per process`() = runTest {
        offer()
        flowStarts(true)
        val controller = controller()

        controller.checkAndStart(activity, launcher)
        controller.checkAndStart(activity, launcher)

        verifyFlowStarted(1)
    }

    @Test
    fun `a flow Play did not launch does not spend the offer`() = runTest {
        offer()
        flowStarts(false)
        val controller = controller()

        controller.checkAndStart(activity, launcher)
        controller.checkAndStart(activity, launcher)

        verifyFlowStarted(2)
    }

    @Test
    fun `cancelling the flexible prompt silences that release`() = runTest {
        offer(versionCode = 50)
        flowStarts(true)
        val controller = controller()

        controller.checkAndStart(activity, launcher)
        controller.onFlowResult(Activity.RESULT_CANCELED)

        assertEquals(50, dismissed)
    }

    @Test
    fun `a Play failure is not mistaken for a refusal`() = runTest {
        offer(versionCode = 50)
        flowStarts(true)
        val controller = controller()

        controller.checkAndStart(activity, launcher)
        controller.onFlowResult(RESULT_IN_APP_UPDATE_FAILED)

        assertEquals(AppUpdatePolicy.NO_VERSION, dismissed)
    }

    @Test
    fun `backing out of an immediate update records nothing`() = runTest {
        offer(versionCode = 50, priority = AppUpdatePolicy.IMMEDIATE_PRIORITY, immediateAllowed = true)
        flowStarts(true)
        val controller = controller()

        controller.checkAndStart(activity, launcher)
        controller.onFlowResult(Activity.RESULT_CANCELED)

        assertEquals(AppUpdatePolicy.NO_VERSION, dismissed)
    }

    @Test
    fun `a finished download prompts a restart until the user says later`() = runTest {
        offer(downloaded = true)
        val controller = controller()

        controller.checkAndStart(activity, launcher)
        assertEquals(AppUpdateUiState.ReadyToInstall, controller.state.value)

        controller.dismissInstallPrompt()
        controller.checkAndStart(activity, launcher)
        assertEquals(AppUpdateUiState.Idle, controller.state.value)
    }

    private companion object {
        /** `com.google.android.play.core.install.model.ActivityResult.RESULT_IN_APP_UPDATE_FAILED`. */
        const val RESULT_IN_APP_UPDATE_FAILED = 1
    }
}
