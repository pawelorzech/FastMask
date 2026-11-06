package com.fastmask.di

import android.content.Context
import android.util.Log
import com.fastmask.BuildConfig
import com.fastmask.data.crash.CrashReporting
import com.fastmask.data.crash.FirebaseCrashlyticsReporter
import com.fastmask.data.crash.crashReportingDataStore
import com.fastmask.domain.crash.CrashReporter
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Wires crash reporting. [CrashReporter] is the only type that touches the
 * Firebase SDK, which stays confined to `data/crash`; everything else goes
 * through [CrashReporting].
 */
@Module
@InstallIn(SingletonComponent::class)
object CrashReportingModule {

    @Provides
    @Singleton
    fun provideCrashReporter(): CrashReporter = FirebaseCrashlyticsReporter()

    /**
     * The reporter is handed over through [Lazy], so that injecting this into
     * `FastMaskApplication` — main thread, before `super.onCreate()` — builds no
     * Firebase object.
     */
    @Provides
    @Singleton
    fun provideCrashReporting(
        @ApplicationContext context: Context,
        reporter: Lazy<CrashReporter>,
    ): CrashReporting = CrashReporting(
        dataStore = context.crashReportingDataStore,
        reporter = { reporter.get() },
        isDebugBuild = BuildConfig.DEBUG,
        onFailure = { error ->
            if (BuildConfig.DEBUG) {
                Log.w("FastMask", "Failed to apply the crash reporting preference", error)
            }
        },
    )
}
