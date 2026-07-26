package com.fastmask.di

import com.fastmask.BuildConfig
import com.fastmask.data.crash.FirebaseCrashlyticsReporter
import com.fastmask.domain.crash.CrashReporter
import com.fastmask.domain.crash.CrashReportingController
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
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
}
