package com.fastmask.domain.usecase

import com.fastmask.domain.repository.AuthRepository
import javax.inject.Inject

class LoginUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(token: String): Result<Unit> {
        if (token.isBlank()) {
            return Result.failure(IllegalArgumentException("API token cannot be empty"))
        }
        return authRepository.login(token)
    }
}
