package com.fastmask.di

import android.util.Log
import com.fastmask.BuildConfig
import com.fastmask.data.crash.FirebaseCrashlyticsReporter
import com.fastmask.data.local.SettingsDataStore
import com.fastmask.domain.crash.CrashReporter
import com.fastmask.domain.crash.CrashReportingController
import com.fastmask.domain.crash.CrashReportingStartup
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import javax.inject.Singleton

/**
 * Wires the crash reporting seam. [CrashReporter] is the only type the rest of
 * the app sees, so the Firebase SDK stays confined to `data/crash`.
 */
@Module
@InstallIn(SingletonComponent::class)
object CrashReportingModule {

    @Provides
    @Singleton
    fun provideCrashReporter(): CrashReporter = FirebaseCrashlyticsReporter()

    @Provides
    @Singleton
    fun provideCrashReportingController(reporter: CrashReporter): CrashReportingController =
        CrashReportingController(reporter = reporter, isDebugBuild = BuildConfig.DEBUG)

    /**
     * Both collaborators are handed over as lambdas, and the controller through
     * [Lazy], so that injecting this into `FastMaskApplication` — which happens
     * on the main thread before `super.onCreate()` — builds no Firebase object.
     */
    @Provides
    @Singleton
    fun provideCrashReportingStartup(
        settingsDataStore: SettingsDataStore,
        controller: Lazy<CrashReportingController>,
    ): CrashReportingStartup = CrashReportingStartup(
        readPreference = { settingsDataStore.crashReportingPreference.first() },
        controller = { controller.get() },
        onFailure = { error ->
            if (BuildConfig.DEBUG) {
                Log.w("FastMask", "Failed to apply the crash reporting preference", error)
            }
        },
    )
}
