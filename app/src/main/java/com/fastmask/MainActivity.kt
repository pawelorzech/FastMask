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
import androidx.navigation.compose.rememberNavController
import com.fastmask.BuildConfig
import com.fastmask.domain.repository.AuthRepository
import com.fastmask.ui.navigation.FastMaskNavHost
import com.fastmask.ui.navigation.NavRoutes
import com.fastmask.ui.theme.FastMaskTheme
import dagger.hilt.android.AndroidEntryPoint
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
        val startDestination = if (authRepository.isLoggedIn()) {
            NavRoutes.EMAIL_LIST
        } else {
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
