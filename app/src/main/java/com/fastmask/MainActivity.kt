package com.fastmask

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.os.LocaleListCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.rememberNavController
import com.fastmask.data.local.SettingsDataStore
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

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    private var isReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // Restore saved language before anything else
        restoreSavedLanguage()

        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        splashScreen.setKeepOnScreenCondition { !isReady }

        enableEdgeToEdge()

        val startDestination = if (authRepository.isLoggedIn()) {
            NavRoutes.EMAIL_LIST
        } else {
            NavRoutes.LOGIN
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

    private fun restoreSavedLanguage() {
        val savedLanguageCode = settingsDataStore.getLanguageBlocking()
        if (savedLanguageCode != null) {
            val localeList = LocaleListCompat.forLanguageTags(savedLanguageCode)
            AppCompatDelegate.setApplicationLocales(localeList)
        }
    }
}
