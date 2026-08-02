package com.fastmask.di

import android.util.Log
import com.fastmask.BuildConfig
import com.fastmask.data.analytics.LogMonetizationAnalytics
import com.fastmask.data.billing.BillingDataSource
import com.fastmask.data.billing.PlayBillingDataSource
import com.fastmask.data.repository.ProRepositoryImpl
import com.fastmask.domain.analytics.MonetizationAnalytics
import com.fastmask.domain.repository.ProRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/** Application-lifetime scope for work that must outlive any single screen. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope

/**
 * The IO dispatcher, injected rather than referenced directly, so a class that
 * owns its own scope can be driven by a test scheduler.
 *
 * Hilt does not honour Kotlin default parameter values on an @Inject
 * constructor, so a plain `= Dispatchers.IO` default is not a usable seam —
 * Dagger reports a missing binding for the parameter's type instead.
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class IoDispatcher

@Module
@InstallIn(SingletonComponent::class)
abstract class BillingModule {

    @Binds
    @Singleton
    abstract fun bindBillingDataSource(impl: PlayBillingDataSource): BillingDataSource

    @Binds
    @Singleton
    abstract fun bindProRepository(impl: ProRepositoryImpl): ProRepository

    @Binds
    @Singleton
    abstract fun bindMonetizationAnalytics(impl: LogMonetizationAnalytics): MonetizationAnalytics

    companion object {
        @Provides
        @Singleton
        @ApplicationScope
        fun provideApplicationScope(): CoroutineScope {
            val handler = CoroutineExceptionHandler { _, throwable ->
                if (BuildConfig.DEBUG) {
                    Log.e("FastMask", "Uncaught exception in application scope", throwable)
                }
            }
            return CoroutineScope(SupervisorJob() + Dispatchers.Default + handler)
        }

        @Provides
        @IoDispatcher
        fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
    }
}
