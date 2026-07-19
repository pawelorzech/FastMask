package com.fastmask.di

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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/** Application-lifetime scope for work that must outlive any single screen. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope

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
        fun provideApplicationScope(): CoroutineScope =
            CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
