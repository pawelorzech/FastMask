package com.fastmask.di

import com.fastmask.data.repository.AuthRepositoryImpl
import com.fastmask.data.repository.MaskedEmailRepositoryImpl
import com.fastmask.domain.repository.AuthRepository
import com.fastmask.domain.repository.MaskedEmailRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        authRepositoryImpl: AuthRepositoryImpl
    ): AuthRepository

    @Binds
    @Singleton
    abstract fun bindMaskedEmailRepository(
        maskedEmailRepositoryImpl: MaskedEmailRepositoryImpl
    ): MaskedEmailRepository
}
