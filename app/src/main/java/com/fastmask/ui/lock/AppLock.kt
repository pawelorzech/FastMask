package com.fastmask.ui.lock

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

private const val ALLOWED_AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

fun canUseAppLock(context: Context): Boolean =
    BiometricManager.from(context)
        .canAuthenticate(ALLOWED_AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

fun showUnlockPrompt(
    activity: FragmentActivity,
    title: String,
    onSuccess: () -> Unit,
    onUnavailable: () -> Unit,
    onFinished: () -> Unit,
) {
    if (!canUseAppLock(activity)) {
        onFinished()
        onUnavailable()
        return
    }
    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setAllowedAuthenticators(ALLOWED_AUTHENTICATORS)
        .build()
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onFinished()
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                // Terminal: the system has dismissed the prompt, so report before branching.
                // onAuthenticationFailed (a wrong finger) is deliberately not overridden:
                // the prompt stays up and must not be released.
                onFinished()
                when (errorCode) {
                    BiometricPrompt.ERROR_NO_BIOMETRICS,
                    BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL,
                    BiometricPrompt.ERROR_HW_NOT_PRESENT,
                    -> onUnavailable()
                    else -> Unit
                }
            }
        },
    )
    prompt.authenticate(promptInfo)
}
