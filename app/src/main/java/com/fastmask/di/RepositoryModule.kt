package com.fastmask.di

import com.fastmask.data.repository.AuthRepositoryImpl
import com.fastmask.data.repository.DemoMaskedEmailRepositoryImpl
import com.fastmask.data.repository.DemoModeActivatorImpl
import com.fastmask.data.repository.MaskedEmailRepositoryDispatcher
import com.fastmask.data.repository.MaskedEmailRepositoryImpl
import com.fastmask.data.repository.QuickMaskGuardImpl
import com.fastmask.domain.repository.AuthRepository
import com.fastmask.domain.repository.DemoSession
import com.fastmask.domain.repository.MaskedEmailRepository
import com.fastmask.domain.repository.QuickMaskGuard
import com.fastmask.domain.usecase.DemoModeActivator
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
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
    abstract fun bindQuickMaskGuard(
        impl: QuickMaskGuardImpl
    ): QuickMaskGuard

    /**
     * Real (JMAP-backed) implementation. Qualified so the dispatcher can request it
     * explicitly. ViewModels never inject this binding directly.
     */
    @Binds
    @Singleton
    @Named("real")
    abstract fun bindRealMaskedEmailRepository(
        impl: MaskedEmailRepositoryImpl
    ): MaskedEmailRepository

    /**
     * In-memory demo implementation. Qualified for the dispatcher; ViewModels never
     * inject this directly.
     */
    @Binds
    @Singleton
    @Named("demo")
    abstract fun bindDemoMaskedEmailRepository(
        impl: DemoMaskedEmailRepositoryImpl
    ): MaskedEmailRepository

    /**
     * Default (unqualified) binding for [MaskedEmailRepository] — a dispatcher that
     * routes to the real or demo implementation at runtime based on the current
     * [com.fastmask.domain.model.AppMode]. This is the one ViewModels receive.
     */
    @Binds
    @Singleton
    abstract fun bindMaskedEmailRepository(
        dispatcher: MaskedEmailRepositoryDispatcher
    ): MaskedEmailRepository

    /** Same singleton as the @Named("demo") binding, exposed for its reset. */
    @Binds
    @Singleton
    abstract fun bindDemoSession(impl: DemoMaskedEmailRepositoryImpl): DemoSession

    /** Single entry point into demo mode, shared by Welcome and Login. */
    @Binds
    @Singleton
    abstract fun bindDemoModeActivator(impl: DemoModeActivatorImpl): DemoModeActivator
}
