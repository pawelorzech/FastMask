package com.fastmask.di

import com.fastmask.BuildConfig
import com.fastmask.domain.crash.CrashReporter
import com.fastmask.domain.crash.CrashReportingController
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * STUB — satisfies the Hilt graph so the module compiles while the crash
 * reporting change is still only a set of tests. The implementer replaces
 * [provideCrashReporter] with the Firebase-backed [CrashReporter].
 */
@Module
@InstallIn(SingletonComponent::class)
object CrashReportingModule {

    @Provides
    @Singleton
    fun provideCrashReporter(): CrashReporter =
        TODO("stub — implemented by the crash reporting change")

    @Provides
    @Singleton
    fun provideCrashReportingController(reporter: CrashReporter): CrashReportingController =
        CrashReportingController(reporter = reporter, isDebugBuild = BuildConfig.DEBUG)
}
