package com.fastmask

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.compose.rememberNavController
import com.fastmask.core.runSuspendCatching
import com.fastmask.data.local.SettingsDataStore
import com.fastmask.domain.model.Accent
import com.fastmask.domain.repository.SessionRepository
import com.fastmask.domain.repository.ProRepository
import com.fastmask.quickmask.NotificationPrompt
import com.fastmask.ui.lock.LockScreen
import com.fastmask.ui.lock.AppLockGate
import com.fastmask.ui.navigation.FastMaskNavHost
import com.fastmask.ui.navigation.NavRoutes
import com.fastmask.ui.navigation.PendingShare
import com.fastmask.ui.navigation.RoutePendingShare
import com.fastmask.ui.theme.FastMaskTheme
import com.fastmask.data.update.AppUpdateController
import com.fastmask.data.update.AppUpdateUiState
import com.fastmask.ui.update.UpdateReadyDialog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var sessionRepository: SessionRepository

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    @Inject
    lateinit var proRepository: ProRepository

    private var isReady = false

    /**
     * Registered as a field so it exists before the Activity is STARTED, which
     * the Activity Result API requires. The result itself needs no handling:
     * a grant makes the quick-create confirmation possible, a denial leaves the
     * existing Toast fallback in place.
     */
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    // Owns the whole "ask once" rule; lazy because it needs the injected setting.
    private val notificationPrompt by lazy {
        NotificationPrompt(Build.VERSION.SDK_INT, settingsDataStore, lifecycleScope)
    }

    @Inject
    lateinit var appUpdateController: AppUpdateController

    /**
     * Play's update flow hands its result back through the Activity Result API.
     * Registered as a property initializer, like the permission launcher above,
     * because `registerForActivityResult` must be called before STARTED.
     */
    private val appUpdateLauncher: ActivityResultLauncher<IntentSenderRequest> =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            appUpdateController.onFlowResult(result.resultCode)
        }

    private val lockGate = AppLockGate()
    private val pendingShare = PendingShare()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        pendingShare.onCreate(intent, savedInstanceState)

        if (!BuildConfig.DEBUG) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE,
            )
            window.decorView.filterTouchesWhenObscured = true
        }

        splashScreen.setKeepOnScreenCondition { !isReady }

        enableEdgeToEdge()

        // Reconcile the entitlement against Play every time the app comes to
        // the foreground (Billing guidance): catches PENDING purchases completed
        // while backgrounded, purchases made on another device, and retries a
        // failed acknowledgement — an unacknowledged purchase is auto-refunded
        // by Play after ~3 days, so the retry cadence matters. Deliberately NOT
        // gated on MONETIZATION_ENABLED: the kill-switch hides entry points,
        // but a purchase that was already charged must still get acknowledged.
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                lifecycleScope.launch { proRepository.refresh() }
            }
        })

        // On every resume, not only on a cold start. That is what re-enters an
        // immediate update the user was pulled out of mid-install, and what
        // notices a background download that finished while FastMask was in
        // another app — including across process death, since the state being
        // read belongs to Play rather than to this process. The controller owns
        // every rule about whether any of that turns into a prompt.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                appUpdateController.checkAndStart(this@MainActivity, appUpdateLauncher)
            }
        }

        // The splash stays up until this block has picked the start destination,
        // so a signed-in user never sees Welcome flash by.
        lifecycleScope.launch {
            var lockAtLaunch = false
            var cachedAccent = Accent.DEFAULT
            val startDestination = runSuspendCatching {
                val signedIn = withContext(Dispatchers.IO) {
                    coroutineScope {
                        // The token check constructs EncryptedSharedPreferences —
                        // Tink + Android Keystore, the slowest read here. Launch it
                        // FIRST so its initialization overlaps the DataStore reads
                        // below instead of queuing behind them on the splash clock.
                        val session = async { sessionRepository.current() }
                        // For the accent: a Pro user's first frame is themed from the cache.
                        val proCached = async { proRepository.awaitCached() }
                        // The lock engages from the preference alone, never the
                        // entitlement: Pro gates turning it on, so a lapsed purchase
                        // cannot disarm a lock the user chose, and turning it off is
                        // always allowed.
                        lockAtLaunch = settingsDataStore.appLockEnabled.first()
                        cachedAccent = settingsDataStore.accent.first()
                        notificationPrompt.load()
                        proCached.await()
                        session.await().isSignedIn
                    }
                }
                if (signedIn) NavRoutes.EMAIL_LIST else NavRoutes.WELCOME
            }.getOrElse {
                // Storage double-fault (see TokenStorage recovery) — fall back to
                // the welcome flow instead of stranding the splash or crashing.
                NavRoutes.WELCOME
            }
            lockGate.restore(savedInstanceState, lockAtLaunch)
            isReady = true

            setContent {
                val proStatus by proRepository.proStatus.collectAsState()
                val accentPref by settingsDataStore.accent.collectAsState(initial = cachedAccent)
                val accent = accentPref.shownFor(proStatus)

                val appLockEnabled by settingsDataStore.appLockEnabled
                    .collectAsState(initial = lockAtLaunch)
                // The DISPLAY gate must not wait for the async Play/store
                // read: on a locked cold start, proStatus still holds its
                // initial FREE for the first frames — keying the gate on it
                // would flash the mask list before the lock lands (P0).
                // The gate is seeded synchronously from the cache above.
                val isLocked = lockGate.locked && appLockEnabled

                // Re-engage the lock whenever the app leaves the foreground.
                // Keyed on the DataStore flag alone (not proStatus): proStatus
                // seeds asynchronously, and an observer waiting for it would
                // miss a backgrounding in the first moments after a cold-start
                // unlock. An enabled flag implies Pro at the time of enabling;
                // the toggle itself stays usable without Pro (anti-lockout).
                DisposableEffect(appLockEnabled) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_STOP && appLockEnabled) {
                            lockGate.relock()
                        }
                    }
                    lifecycle.addObserver(observer)
                    onDispose { lifecycle.removeObserver(observer) }
                }

                FastMaskTheme(accent = accent) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        // The controller lives OUTSIDE the lock gate: a
                        // lock/unlock cycle must not destroy the back stack and
                        // the screen ViewModels (half-typed create form, unsaved
                        // edit). Only the NavHost content is gated.
                        val navController = rememberNavController()
                        val session by sessionRepository.session.collectAsState()
                        val signedIn = session.isSignedIn
                        // Re-evaluated on every session change, not once at launch: a user
                        // who starts signed out and signs in without leaving must still be
                        // asked. The gate owns the "at most once" part.
                        LaunchedEffect(session, isLocked) {
                            maybeRequestNotificationPermission(
                                signedIn = signedIn,
                                locked = isLocked,
                                demoMode = session.isDemo,
                            )
                        }

                        if (isLocked) {
                            // Content behind the gate is not composed at all.
                            LockScreen(
                                onUnlockClick = ::requestUnlock,
                                promptActive = lockGate.promptShowing,
                                unlocked = lockGate.unlocked,
                                onUnlockedAnimationFinished = lockGate::onUnlockAnimationFinished,
                            )
                            LaunchedEffect(Unit) { requestUnlock() }
                        } else {
                            RoutePendingShare(
                                pending = pendingShare,
                                signedIn = signedIn,
                                locked = isLocked,
                                navController = navController,
                            )
                            FastMaskNavHost(
                                navController = navController,
                                startDestination = startDestination
                            )

                            // Inside the unlocked branch on purpose: a restart
                            // prompt must not be the first thing drawn over the
                            // biometric gate. It reappears on the next check
                            // anyway, and the download it offers to install is
                            // not going anywhere.
                            val updateState by appUpdateController.state.collectAsStateWithLifecycle()
                            if (updateState == AppUpdateUiState.ReadyToInstall) {
                                UpdateReadyDialog(
                                    onInstall = appUpdateController::completeInstall,
                                    onDismiss = appUpdateController::dismissInstallPrompt,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingShare.onNewIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        lockGate.save(outState)
        pendingShare.save(outState)
    }

    private fun requestUnlock() = lockGate.requestUnlock(this, getString(R.string.app_lock_prompt_title))

    /**
     * Asks for POST_NOTIFICATIONS off a stream of session snapshots, not one
     * startup guess.
     *
     * The reported miss was a user who launched signed out and signed in
     * without leaving: one onCreate-time check off the start destination never
     * saw the later real session. The gate now sees every route/lock/app-mode
     * snapshot and decides when the one ask is warranted. The launcher call is
     * wrapped in `runCatching` because an ActivityResultLauncher that is not
     * registered for this lifecycle state throws; a thrown ask is better than a
     * crash, and the Toast fallback still covers quick-create.
     */
    private fun maybeRequestNotificationPermission(
        signedIn: Boolean,
        locked: Boolean,
        demoMode: Boolean,
    ) {
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        // The rule, including "assume already asked until the flag is loaded" and
        // recording the ask, lives in NotificationPrompt; this only launches.
        val shouldAsk = notificationPrompt.shouldAsk(
            permissionGranted = granted,
            signedIn = signedIn,
            locked = locked,
            demoMode = demoMode,
        )
        if (!shouldAsk) return

        runCatching { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
    }

}
