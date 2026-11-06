package com.fastmask.core

import javax.inject.Qualifier

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
