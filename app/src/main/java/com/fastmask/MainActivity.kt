package com.fastmask

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.fastmask.BuildConfig
import com.fastmask.domain.repository.AuthRepository
import com.fastmask.ui.navigation.FastMaskNavHost
import com.fastmask.ui.navigation.NavRoutes
import com.fastmask.ui.theme.FastMaskTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var authRepository: AuthRepository

    private var isReady = false

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
        lifecycleScope.launch {
            val startDestination = try {
                withContext(Dispatchers.IO) {
                    if (authRepository.isLoggedIn()) NavRoutes.EMAIL_LIST else NavRoutes.WELCOME
                }
            } catch (e: Exception) {
                // Storage double-fault (see TokenStorage recovery) — fall back to
                // the welcome flow instead of stranding the splash or crashing.
                NavRoutes.WELCOME
            }
            isReady = true

            setContent {
                FastMaskTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        val navController = rememberNavController()

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
