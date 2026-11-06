package com.fastmask.di

import com.fastmask.data.local.SettingsDataStore
import com.fastmask.data.repository.MaskedEmailRepositoryDispatcher
import com.fastmask.data.session.SessionRepositoryImpl
import com.fastmask.domain.repository.AppLockSetting
import com.fastmask.domain.repository.MaskedEmailRepository
import com.fastmask.domain.repository.SessionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindSessionRepository(impl: SessionRepositoryImpl): SessionRepository

    @Binds
    abstract fun bindAppLockSetting(impl: SettingsDataStore): AppLockSetting

    @Binds
    abstract fun bindMaskedEmailRepository(impl: MaskedEmailRepositoryDispatcher): MaskedEmailRepository
}
