package com.fastmask.di

import com.fastmask.data.analytics.LogMonetizationAnalytics
import com.fastmask.data.billing.BillingDataSource
import com.fastmask.data.billing.PlayBillingDataSource
import com.fastmask.data.repository.ProRepositoryImpl
import com.fastmask.domain.analytics.MonetizationAnalytics
import com.fastmask.domain.repository.ProRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class BillingModule {

    @Binds
    abstract fun bindBillingDataSource(impl: PlayBillingDataSource): BillingDataSource

    @Binds
    abstract fun bindProRepository(impl: ProRepositoryImpl): ProRepository

    @Binds
    abstract fun bindMonetizationAnalytics(impl: LogMonetizationAnalytics): MonetizationAnalytics
}
