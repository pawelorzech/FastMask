package com.fastmask.ui.update

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.fastmask.data.update.AppUpdateAvailability
import com.fastmask.data.update.AppUpdateDecision
import com.fastmask.data.update.AppUpdateFlowType
import com.fastmask.data.update.AppUpdatePolicy
import com.fastmask.data.update.AppUpdatePreferences
import com.fastmask.data.update.AppUpdateSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** What the UI is allowed to know. Free of Play types on purpose — see [AppUpdateController]. */
sealed interface AppUpdateUiState {
    data object Idle : AppUpdateUiState

    /** A flexible update is coming down in the background; the app stays fully usable. */
    data object Downloading : AppUpdateUiState

    /** Bytes are on the device. The only thing left is the user agreeing to restart. */
    data object ReadyToInstall : AppUpdateUiState
}

/**
 * The only place in FastMask that touches Play's In-App Update API.
 *
 * Two rules, for the same reasons the Billing seam has them:
 *
 *  1. **It cannot take down the app.** `requestAppUpdateInfo` throws outright when the app was not
 *     installed by Play — a sideloaded APK, an emulator without Play services — and every one of
 *     those is a supported state. Every call is guarded and every failure is silent, because a
 *     user who cannot be offered an update has lost nothing.
 *  2. **It reports nothing.** No analytics, no crash keys, no counters that leave the device. The
 *     dismissed version code is stored so the app can stop asking, and for nothing else.
 *
 * The Play types stay inside this class. [AppUpdateInfo] in particular is a mutable, unstable
 * object that Play refuses to accept twice, so exposing it to Compose would mean recomposing
 * against a value that silently expires; the UI sees [AppUpdateUiState] instead.
 */
class AppUpdateController private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val preferences = AppUpdatePreferences(appContext)

    private val manager: AppUpdateManager by lazy { AppUpdateManagerFactory.create(appContext) }

    private val uiState = MutableStateFlow<AppUpdateUiState>(AppUpdateUiState.Idle)
    val state: StateFlow<AppUpdateUiState> = uiState.asStateFlow()

    /**
     * One flexible offer per process, however many times the check runs.
     *
     * The check deliberately runs on every resume — that is what re-enters a stalled immediate
     * update and what notices a download that finished while the app was away — so without this
     * the user would be asked again every time they came back from another app.
     */
    @Volatile
    private var hasPromptedThisProcess = false

    /** "Later" on the restart prompt, remembered for as long as this process lives. */
    @Volatile
    private var hasDismissedInstallPrompt = false

    /** What is currently in Play's hands, so the activity result knows what it is answering. */
    @Volatile
    private var pendingFlow: AppUpdateFlowType? = null

    @Volatile
    private var pendingVersionCode = AppUpdatePolicy.NO_VERSION

    private val installListener = InstallStateUpdatedListener { installState ->
        when (installState.installStatus()) {
            InstallStatus.DOWNLOADING -> uiState.value = AppUpdateUiState.Downloading
            InstallStatus.DOWNLOADED -> {
                uiState.value = AppUpdateUiState.ReadyToInstall
                unregisterListener()
            }
            // A failed, cancelled or already-installed update has nothing left to say. Drop back
            // to Idle rather than leaving a stale "downloading" the user can never resolve.
            InstallStatus.FAILED, InstallStatus.CANCELED, InstallStatus.INSTALLED -> {
                uiState.value = AppUpdateUiState.Idle
                unregisterListener()
            }
            else -> Unit
        }
    }

    @Volatile
    private var isListening = false

    /**
     * The single entry point. Reads Play's view of the world, applies [AppUpdatePolicy], and does
     * whatever it says — which is usually nothing.
     *
     * Call it on every resume. A cold-start-only check would miss both states that exist precisely
     * because the app was interrupted: an immediate flow the user backed out of mid-install, and a
     * flexible download that completed while FastMask was in the background.
     */
    suspend fun checkAndStart(
        activity: Activity,
        launcher: ActivityResultLauncher<IntentSenderRequest>,
    ) {
        // Gated on the install source, never on the build type. Internal app sharing is the only
        // realistic way to test this and those builds are debuggable, so gating on debuggability
        // would switch the feature off in exactly the builds that exist to exercise it; conversely
        // a sideloaded release build would otherwise fail this check on every resume for nothing.
        if (!isInstalledFromPlay(appContext)) return

        val info = requestInfo() ?: return
        val snapshot = snapshotOf(info)
        val decision = AppUpdatePolicy.decide(
            snapshot = snapshot,
            dismissedVersionCode = preferences.dismissedVersionCode,
            hasPromptedThisProcess = hasPromptedThisProcess,
        )

        when (decision) {
            AppUpdateDecision.None -> Unit

            AppUpdateDecision.CompleteInstall ->
                if (!hasDismissedInstallPrompt) uiState.value = AppUpdateUiState.ReadyToInstall

            is AppUpdateDecision.Start -> startFlow(activity, launcher, info, snapshot, decision.type)
        }
    }

    private fun startFlow(
        activity: Activity,
        launcher: ActivityResultLauncher<IntentSenderRequest>,
        info: AppUpdateInfo,
        snapshot: AppUpdateSnapshot,
        type: AppUpdateFlowType,
    ) {
        val playType = when (type) {
            AppUpdateFlowType.FLEXIBLE -> AppUpdateType.FLEXIBLE
            AppUpdateFlowType.IMMEDIATE -> AppUpdateType.IMMEDIATE
        }
        // Registered before the flow starts, not after: the download can begin the instant the
        // user accepts, and a listener attached afterwards would miss the DOWNLOADED that follows
        // a very small update on a fast connection.
        if (type == AppUpdateFlowType.FLEXIBLE) registerListener()

        val started = runCatching {
            manager.startUpdateFlowForResult(
                info,
                launcher,
                AppUpdateOptions.newBuilder(playType).build(),
            )
        }.getOrDefault(false)

        if (!started) {
            unregisterListener()
            return
        }
        pendingFlow = type
        pendingVersionCode = snapshot.availableVersionCode
        // An immediate update is meant to come back on every launch until it is installed, so it
        // never spends the once-per-process offer.
        if (type == AppUpdateFlowType.FLEXIBLE) hasPromptedThisProcess = true
    }

    /**
     * The user's answer to Play's own dialog.
     *
     * Only a declined *flexible* offer is remembered. Backing out of an immediate flow is not a
     * refusal of the release — it is a user who needed their phone back — and recording it would
     * silence the one class of update that exists to be insistent.
     */
    fun onFlowResult(resultCode: Int) {
        val flow = pendingFlow
        val versionCode = pendingVersionCode
        pendingFlow = null
        pendingVersionCode = AppUpdatePolicy.NO_VERSION

        if (resultCode == Activity.RESULT_OK) return
        if (flow != AppUpdateFlowType.FLEXIBLE) return

        unregisterListener()
        uiState.value = AppUpdateUiState.Idle

        // RESULT_IN_APP_UPDATE_FAILED is a Play failure, not a user refusal.
        // Persist only an explicit cancellation; otherwise a transient Play
        // problem would suppress this release forever on this device.
        if (resultCode != Activity.RESULT_CANCELED) return
        if (versionCode == AppUpdatePolicy.NO_VERSION) return
        preferences.dismissedVersionCode = versionCode
    }

    /** Hands the finished download back to Play, which restarts FastMask into the new version. */
    fun completeInstall() {
        runCatching { manager.completeUpdate() }
    }

    /**
     * "Later" on the restart prompt. The bytes stay on disk and Play installs them the next time
     * the app is stopped anyway, so this hides a dialog rather than cancelling anything — which is
     * why it records nothing durable.
     *
     * It does hold for the rest of the process. Without that the resume check would put the same
     * dialog straight back the next time the user returned from another app, which is the exact
     * nagging the per-process guard exists to prevent — and here it would be nagging about
     * something Play is going to do by itself regardless.
     */
    fun dismissInstallPrompt() {
        hasDismissedInstallPrompt = true
        uiState.value = AppUpdateUiState.Idle
    }

    private suspend fun requestInfo(): AppUpdateInfo? = runCatching {
        suspendCancellableCoroutine { continuation ->
            manager.appUpdateInfo
                .addOnCompleteListener { task ->
                    continuation.resume(if (task.isSuccessful) task.result else null)
                }
        }
    }.getOrNull()

    private fun snapshotOf(info: AppUpdateInfo): AppUpdateSnapshot = AppUpdateSnapshot(
        availability = when (info.updateAvailability()) {
            UpdateAvailability.UPDATE_AVAILABLE -> AppUpdateAvailability.AVAILABLE
            UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS -> AppUpdateAvailability.IN_PROGRESS
            else -> AppUpdateAvailability.NONE
        },
        availableVersionCode = info.availableVersionCode(),
        isFlexibleAllowed = info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE),
        isImmediateAllowed = info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE),
        priority = info.updatePriority(),
        stalenessDays = info.clientVersionStalenessDays(),
        isDownloaded = info.installStatus() == InstallStatus.DOWNLOADED,
    )

    private fun registerListener() {
        if (isListening) return
        runCatching { manager.registerListener(installListener) }
            .onSuccess { isListening = true }
    }

    private fun unregisterListener() {
        if (!isListening) return
        runCatching { manager.unregisterListener(installListener) }
        isListening = false
    }

    companion object {

        @Volatile
        private var instance: AppUpdateController? = null

        /**
         * Process-wide: a second controller would carry its own [hasPromptedThisProcess], so a
         * configuration change would hand the user a fresh offer of the update they just declined.
         */
        fun get(context: Context): AppUpdateController =
            instance ?: synchronized(this) {
                instance ?: AppUpdateController(context).also { instance = it }
            }

        /**
         * True when Play installed this app, which is the only case where the update API works at
         * all. Internal app sharing installs come from the Play Store too, so this stays true for
         * the builds used to test the feature.
         */
        fun isInstalledFromPlay(context: Context): Boolean = runCatching {
            val packages = context.packageManager
            val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                packages.getInstallSourceInfo(context.packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                packages.getInstallerPackageName(context.packageName)
            }
            installer == PLAY_STORE_PACKAGE
        }.getOrDefault(false)

        private const val PLAY_STORE_PACKAGE = "com.android.vending"
    }
}
