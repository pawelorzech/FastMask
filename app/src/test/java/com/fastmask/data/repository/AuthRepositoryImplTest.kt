package com.fastmask.data.repository

import com.fastmask.data.api.JmapApi
import com.fastmask.data.local.ExportCache
import com.fastmask.data.local.MaskedEmailCache
import com.fastmask.data.local.SettingsDataStore
import com.fastmask.data.local.TokenStorage
import com.fastmask.domain.model.AppMode
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AuthRepositoryImplTest {

    private val tokenStorage = mockk<TokenStorage>(relaxed = true)
    private val jmapApi = mockk<JmapApi>(relaxed = true)
    private val settings = mockk<SettingsDataStore>(relaxed = true)
    private val exportCache = mockk<ExportCache>(relaxed = true)
    private val maskCache = mockk<MaskedEmailCache>(relaxed = true)

    private val repository = AuthRepositoryImpl(tokenStorage, jmapApi, settings, exportCache, maskCache)

    /**
     * A CSV export holds every mask in plaintext under cacheDir. Ageing it out
     * after an hour is right while signed in, but sign-out is when the
     * account's data should stop being on the device — otherwise an export
     * written a minute earlier outlives the session it came from.
     */
    @Test
    fun `logout clears the plaintext export cache`() = runTest {
        repository.logout()

        verify { exportCache.clear() }
    }

    @Test
    fun `logout drops the token and the cached JMAP session`() = runTest {
        repository.logout()

        verify { tokenStorage.clearToken() }
        verify { jmapApi.clearSession() }
    }

    @Test
    fun `logout resets demo mode and the tutorial flag`() = runTest {
        repository.logout()

        coVerify { settings.setAppMode(AppMode.REAL) }
        coVerify { settings.setTutorialCompleted(false) }
    }
}
