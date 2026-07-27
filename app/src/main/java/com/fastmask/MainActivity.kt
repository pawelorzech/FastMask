package com.fastmask

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.fastmask.data.local.ProEntitlementStore
import com.fastmask.data.local.SettingsDataStore
import com.fastmask.domain.model.Accent
import com.fastmask.domain.model.AppMode
import com.fastmask.domain.model.ProStatus
import com.fastmask.domain.share.ShareInbox
import com.fastmask.domain.share.ShareIntentPolicy
import com.fastmask.domain.share.ShareRequest
import com.fastmask.domain.share.ShareRoute
import com.fastmask.domain.share.ShareRouter
import com.fastmask.domain.share.SharedLinkParser
import com.fastmask.domain.repository.AuthRepository
import com.fastmask.domain.repository.ProRepository
import com.fastmask.quickmask.NotificationPermissionGate
import com.fastmask.ui.lock.LockScreen
import com.fastmask.ui.lock.showUnlockPrompt
import com.fastmask.ui.navigation.FastMaskNavHost
import com.fastmask.ui.navigation.NavRoutes
import com.fastmask.ui.theme.FastMaskTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    @Inject
    lateinit var proRepository: ProRepository

    @Inject
    lateinit var proEntitlementStore: ProEntitlementStore

    private var isReady = false

    /**
     * Registered as a field so it exists before the Activity is STARTED, which
     * the Activity Result API requires. The result itself needs no handling:
     * a grant makes the quick-create confirmation possible, a denial leaves the
     * existing Toast fallback in place.
     */
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val notificationPermissionGate = NotificationPermissionGate(Build.VERSION.SDK_INT)

    /** Biometric app-lock gate (Pro). True = LockScreen covers all content. */
    private val locked = mutableStateOf(false)
    private val pendingShare = mutableStateOf<ShareRequest?>(null)
    private val shareInbox = ShareInbox()
    // Until the persisted flag has been read, the safe answer is "already
    // asked": a startup race must never spend the one system prompt.
    private var notificationPromptShown: Boolean = true

    /**
     * True once the current launch intent's share has been routed to the create
     * screen.
     *
     * A configuration change re-runs [onCreate] with the SAME ACTION_SEND
     * intent, so without this flag the share is replayed on every rotation —
     * re-opening a create form the user had already filled in or dismissed.
     * It is persisted in the instance state because a rotation is exactly the
     * event that would otherwise reset it.
     */
    private var shareConsumed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        shareConsumed = savedInstanceState?.getBoolean(KEY_SHARE_CONSUMED) ?: false
        pendingShare.value = if (shareConsumed) null else shareRequestFromIntent(intent)

        if (!BuildConfig.DEBUG) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE,
            )
            window.decorView.filterTouchesWhenObscured = true
        }

        splashScreen.setKeepOnScreenCondition { !isReady }

        enableEdgeToEdge()

        // Users who have a real Fastmail token, or who have explicitly entered
        // demo mode, jump straight to the list. Everyone else starts on the
        // welcome screen — the entry point for "Sign in with Fastmail" and
        // "Try demo". See [AuthRepositoryImpl.isLoggedIn] for the demo bypass.
        //
        // isLoggedIn() touches EncryptedSharedPreferences (Tink/KeyStore init +
        // disk read) and DataStore — both blocking I/O. It runs off the main
        // thread here; the splash stays up until the destination is known, so
        // there is no flash of the wrong screen.
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

        lifecycleScope.launch {
            var lockAtLaunch = false
            var cachedPro = false
            var cachedAccent = Accent.DEFAULT
            var cachedAppMode = AppMode.REAL
            var loggedInAtLaunch = false
            val startDestination = try {
                withContext(Dispatchers.IO) {
                    // App lock engages from the last VERIFIED entitlement (cache):
                    // the Play reconciliation may race this read, and a privacy
                    // gate should not wait on Play.
                    cachedPro = proEntitlementStore.read() == ProStatus.PRO
                    lockAtLaunch = settingsDataStore.appLockEnabled.first() && cachedPro
                    cachedAccent = settingsDataStore.accent.first()
                    notificationPromptShown = settingsDataStore.notificationPromptShown()
                    cachedAppMode = settingsDataStore.appMode.first()
                    loggedInAtLaunch = authRepository.isLoggedIn()
                }
                if (loggedInAtLaunch) NavRoutes.EMAIL_LIST else NavRoutes.WELCOME
            } catch (e: Exception) {
                // Storage double-fault (see TokenStorage recovery) — fall back to
                // the welcome flow instead of stranding the splash or crashing.
                loggedInAtLaunch = false
                NavRoutes.WELCOME
            }
            // A config change (rotation) recreates the Activity mid-session;
            // don't demand a fresh unlock when the previous instance was open.
            // Only a bundle from THIS process may be trusted: on API < 28
            // onSaveInstanceState runs BEFORE onStop, so a bundle persisted by a
            // process that later died in the background carries locked=false
            // from before the ON_STOP re-lock — restoring it would bypass the
            // lock. A process token distinguishes rotation from process death.
            val sameProcess =
                savedInstanceState?.getString(KEY_PROCESS_TOKEN) == processToken
            locked.value = if (savedInstanceState != null && sameProcess) {
                savedInstanceState.getBoolean(KEY_LOCKED, lockAtLaunch)
            } else {
                lockAtLaunch
            }
            isReady = true

            setContent {
                val proStatus by proRepository.proStatus.collectAsState()
                val accentPref by settingsDataStore.accent.collectAsState(initial = cachedAccent)
                // Accents are a Pro feature — losing Pro gracefully falls back
                // to the classic amber without touching the stored preference.
                // The cache snapshot from the IO read above covers the first
                // frames before the repository's async seed lands, so a Pro
                // user's accent doesn't flash amber on every cold start. (If
                // Play revokes Pro mid-session the accent lingers until the
                // next launch — cosmetic, self-correcting.)
                val accent = if (proStatus.isPro || (proStatus == ProStatus.FREE && cachedPro)) {
                    accentPref
                } else {
                    Accent.DEFAULT
                }

                val appLockEnabled by settingsDataStore.appLockEnabled
                    .collectAsState(initial = lockAtLaunch)
                // The DISPLAY gate must not wait for the async Play/store
                // read: on a locked cold start, proStatus still holds its
                // initial FREE for the first frames — keying the gate on it
                // would flash the mask list before the lock lands (P0).
                // locked.value is seeded synchronously from the cache above.
                val isLocked = locked.value && appLockEnabled

                // Re-engage the lock whenever the app leaves the foreground.
                // Keyed on the DataStore flag alone (not proStatus): proStatus
                // seeds asynchronously, and an observer waiting for it would
                // miss a backgrounding in the first moments after a cold-start
                // unlock. An enabled flag implies Pro at the time of enabling;
                // the toggle itself stays usable without Pro (anti-lockout).
                DisposableEffect(appLockEnabled) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_STOP && appLockEnabled) {
                            locked.value = true
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
                        val navBackStackEntry by navController.currentBackStackEntryAsState()
                        val appMode by settingsDataStore.appMode.collectAsState(initial = cachedAppMode)
                        // Live session state, not a launch-time snapshot: the reproduced bug was a
                        // process that started signed out and signed in without leaving. WELCOME and
                        // LOGIN are the only destinations reachable without a session (demo mode goes
                        // to EMAIL_LIST and can create masks, so it counts as signed in). A null route
                        // means the NavHost has not composed yet — fall back to the launch answer.
                        val signedIn = when (navBackStackEntry?.destination?.route) {
                            null -> loggedInAtLaunch
                            NavRoutes.WELCOME, NavRoutes.LOGIN -> false
                            else -> true
                        }
                        // One snapshot per session-state change, instead of one evaluation in
                        // onCreate: the reported bug was a user who launched signed out and signed
                        // in without leaving (welcome -> login -> list). The route is the sign-in
                        // signal, and it is null while the lock gate is up because the NavHost is
                        // not composed behind it — which is exactly the deferral we want. The gate
                        // owns the "at most once" part.
                        LaunchedEffect(navBackStackEntry?.destination?.route, isLocked, appMode) {
                            maybeRequestNotificationPermission(
                                signedIn = navBackStackEntry?.destination?.route == NavRoutes.EMAIL_LIST,
                                locked = isLocked,
                                demoMode = appMode == AppMode.DEMO,
                            )
                        }

                        if (isLocked) {
                            // Content behind the gate is not composed at all.
                            LockScreen(onUnlockClick = ::requestUnlock)
                            LaunchedEffect(Unit) { requestUnlock() }
                        } else {
                            // This must stay INSIDE the unlocked branch: a pending share waits behind
                            // the biometric gate (ShareRoute.WaitForUnlock) until content is allowed to
                            // compose. A share must never become a way past the app lock, and
                            // ShareRouter.consumes() refuses to clear a share that is still waiting.
                            LaunchedEffect(pendingShare.value, signedIn, isLocked) {
                                val route = ShareRouter.route(
                                    request = pendingShare.value,
                                    signedIn = signedIn,
                                    locked = isLocked,
                                )
                                when (route) {
                                    is ShareRoute.OpenCreate ->
                                        navController.navigate(NavRoutes.createEmail(route.prefill)) {
                                            launchSingleTop = true
                                        }
                                    // Dropped, but never in silence: the create form cannot be
                                    // submitted without a session, and holding the text across an
                                    // external OAuth round-trip (and a possible process death) to
                                    // replay it minutes later is a surprise the user cannot connect
                                    // to anything they did. A Toast rather than a Snackbar because
                                    // MainActivity has no Scaffold/SnackbarHost and this lands on the
                                    // welcome screen, where there is nothing to obscure; adding a host
                                    // around the NavHost would be an out-of-scope change to an
                                    // edge-to-edge layout.
                                    ShareRoute.RejectSignedOut ->
                                        Toast.makeText(
                                            this@MainActivity,
                                            R.string.share_requires_sign_in,
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    ShareRoute.WaitForUnlock -> Unit
                                    ShareRoute.Idle -> Unit
                                }
                                if (ShareRouter.consumes(route)) {
                                    shareConsumed = true
                                    pendingShare.value = null
                                }
                            }
                            FastMaskNavHost(
                                navController = navController,
                                startDestination = startDestination
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Only a genuinely new share re-arms the flag; an unrelated intent must
        // not resurrect one that was already routed.
        val share = shareRequestFromIntent(intent) ?: return
        shareConsumed = false
        pendingShare.value = share
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_LOCKED, locked.value)
        outState.putString(KEY_PROCESS_TOKEN, processToken)
        outState.putBoolean(KEY_SHARE_CONSUMED, shareConsumed)
    }

    /**
     * True while a system unlock prompt is on screen.
     *
     * Two paths ask for one: the [LaunchedEffect] that fires when the gate
     * appears, and the LockScreen button. Without the guard a tap landing while
     * the automatic prompt was already up called `authenticate()` twice, which
     * cancels the first prompt.
     *
     * Released by `onFinished` on EVERY terminal outcome — including a cancel
     * or a lockout — so the retry button can never be left dead. That is the
     * whole risk in this guard, and it is why the release is not conditional.
     */
    private var unlockPromptShowing = false

    private fun requestUnlock() {
        if (unlockPromptShowing) return
        unlockPromptShowing = true
        showUnlockPrompt(
            activity = this,
            title = getString(R.string.app_lock_prompt_title),
            onSuccess = { locked.value = false },
            // Device can no longer authenticate at all (screen lock removed) —
            // unlock rather than brick; equivalent to a device without a lock.
            onUnavailable = { locked.value = false },
            onFinished = { unlockPromptShowing = false },
        )
    }

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
        val shouldAsk = notificationPermissionGate.shouldPrompt(
            permissionGranted = granted,
            alreadyAsked = notificationPromptShown,
            signedIn = signedIn,
            locked = locked,
            demoMode = demoMode,
        )
        if (!shouldAsk) return

        // Recorded before the dialog resolves: whatever the user answers, the app
        // has now had its one ask. The gate already latched in memory, so this
        // write only has to survive to the NEXT process.
        notificationPromptShown = true
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { settingsDataStore.setNotificationPromptShown(true) }
            }
        }
        runCatching { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
    }

    private fun shareRequestFromIntent(intent: Intent?): ShareRequest? {
        // getCharSequenceExtra, not getStringExtra: EXTRA_TEXT is a CharSequence
        // and a Spanned from the sending app makes getStringExtra return null.
        // The type/length rules live in ShareIntentPolicy, where they are tested.
        val sharedText: String = ShareIntentPolicy.sharedText(
            action = intent?.action,
            type = intent?.type,
            text = intent?.getCharSequenceExtra(Intent.EXTRA_TEXT),
        ) ?: return null
        return shareInbox.offer(prefill = SharedLinkParser.parse(sharedText))
    }

    private companion object {
        const val KEY_LOCKED = "fastmask_locked"
        const val KEY_PROCESS_TOKEN = "fastmask_process_token"
        const val KEY_SHARE_CONSUMED = "fastmask_share_consumed"

        /**
         * Identifies this OS process. A saved-instance bundle whose token does
         * not match was written by a previous process (background process
         * death) — its lock flag is stale and must not be trusted.
         */
        val processToken: String = java.util.UUID.randomUUID().toString()
    }
}
