package com.fastmask.core

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope

/**
 * Hilt does not honour Kotlin default parameter values on an @Inject
 * constructor, so a plain `= Dispatchers.IO` default is not a usable seam:
 * Dagger reports a missing binding for the parameter's type instead.
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class IoDispatcher
