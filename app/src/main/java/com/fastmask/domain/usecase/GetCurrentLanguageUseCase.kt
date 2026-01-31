package com.fastmask.domain.usecase

import com.fastmask.data.local.SettingsDataStore
import com.fastmask.domain.model.Language
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class GetCurrentLanguageUseCase @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) {
    operator fun invoke(): Flow<Language?> {
        return settingsDataStore.languageFlow.map { code ->
            Language.fromCode(code)
        }
    }

    fun getBlocking(): Language? {
        return Language.fromCode(settingsDataStore.getLanguageBlocking())
    }
}
