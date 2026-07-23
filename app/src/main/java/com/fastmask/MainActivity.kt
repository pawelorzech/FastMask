package com.fastmask

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.fastmask.data.local.ProEntitlementStore
import com.fastmask.data.local.SettingsDataStore
import com.fastmask.domain.model.Accent
import com.fastmask.domain.model.ProStatus
import com.fastmask.domain.repository.AuthRepository
import com.fastmask.domain.repository.ProRepository
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

    /** Biometric app-lock gate (Pro). True = LockScreen covers all content. */
    private val locked = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

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
            val startDestination = try {
                withContext(Dispatchers.IO) {
                    // App lock engages from the last VERIFIED entitlement (cache):
                    // the Play reconciliation may race this read, and a privacy
                    // gate should not wait on Play.
                    cachedPro = proEntitlementStore.read() == ProStatus.PRO
                    lockAtLaunch = settingsDataStore.appLockEnabled.first() && cachedPro
                    cachedAccent = settingsDataStore.accent.first()
                    if (authRepository.isLoggedIn()) NavRoutes.EMAIL_LIST else NavRoutes.WELCOME
                }
            } catch (e: Exception) {
                // Storage double-fault (see TokenStorage recovery) — fall back to
                // the welcome flow instead of stranding the splash or crashing.
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

                        if (isLocked) {
                            // Content behind the gate is not composed at all.
                            LockScreen(onUnlockClick = ::requestUnlock)
                            LaunchedEffect(Unit) { requestUnlock() }
                        } else {
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_LOCKED, locked.value)
        outState.putString(KEY_PROCESS_TOKEN, processToken)
    }

    private fun requestUnlock() {
        showUnlockPrompt(
            activity = this,
            title = getString(R.string.app_lock_prompt_title),
            onSuccess = { locked.value = false },
            // Device can no longer authenticate at all (screen lock removed) —
            // unlock rather than brick; equivalent to a device without a lock.
            onUnavailable = { locked.value = false },
        )
    }

    private companion object {
        const val KEY_LOCKED = "fastmask_locked"
        const val KEY_PROCESS_TOKEN = "fastmask_process_token"

        /**
         * Identifies this OS process. A saved-instance bundle whose token does
         * not match was written by a previous process (background process
         * death) — its lock flag is stale and must not be trusted.
         */
        val processToken: String = java.util.UUID.randomUUID().toString()
    }
}
