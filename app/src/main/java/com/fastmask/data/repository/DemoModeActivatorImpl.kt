package com.fastmask.data.repository

import com.fastmask.data.local.SettingsDataStore
import com.fastmask.domain.model.AppMode
import com.fastmask.domain.repository.DemoSession
import com.fastmask.domain.usecase.DemoModeActivator
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DemoModeActivatorImpl @Inject constructor(
    private val demoSession: DemoSession,
    private val settingsDataStore: SettingsDataStore,
) : DemoModeActivator {

    /**
     * Moved verbatim out of `WelcomeViewModel` so the login screen's demo exit
     * runs the same sequence rather than a second copy of it.
     *
     * Order matters. [DemoSession.reset] first: the demo repository is a
     * process singleton, so without it a second demo reopens the first one's
     * edits. Then [AppMode.DEMO] *before* clearing the tutorial flag, so
     * downstream observers already see the new mode when the flag changes.
     *
     * Throws on a DataStore write failure — callers must not navigate into a
     * demo whose mode flag was never persisted.
     */
    override suspend fun activate() {
        demoSession.reset()
        settingsDataStore.setAppMode(AppMode.DEMO)
        settingsDataStore.setTutorialCompleted(false)
    }
}
