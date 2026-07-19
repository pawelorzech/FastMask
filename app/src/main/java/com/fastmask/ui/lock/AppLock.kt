package com.fastmask.ui.lock

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

private const val ALLOWED_AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

/** Whether the device can protect the app (biometrics or screen lock set up). */
fun canUseAppLock(context: Context): Boolean =
    BiometricManager.from(context)
        .canAuthenticate(ALLOWED_AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

/**
 * Shows the system unlock prompt (biometric with device-credential fallback).
 *
 * @param onUnavailable fired when the device can no longer authenticate at all
 *   (e.g. screen lock removed) — callers unlock rather than brick the app,
 *   matching the security level of a device without any lock.
 */
fun showUnlockPrompt(
    activity: FragmentActivity,
    title: String,
    onSuccess: () -> Unit,
    onUnavailable: () -> Unit,
) {
    if (!canUseAppLock(activity)) {
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
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                when (errorCode) {
                    BiometricPrompt.ERROR_NO_BIOMETRICS,
                    BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL,
                    BiometricPrompt.ERROR_HW_NOT_PRESENT,
                    -> onUnavailable()
                    // Cancel / lockout: stay locked; LockScreen offers retry.
                    else -> Unit
                }
            }
        },
    )
    prompt.authenticate(promptInfo)
}
