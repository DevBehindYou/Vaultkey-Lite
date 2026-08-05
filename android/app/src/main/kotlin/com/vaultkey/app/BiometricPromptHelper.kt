package com.vaultkey.app

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import javax.crypto.Cipher

/**
 * Same helper that existed in the pre-Flutter Compose UI
 * (legacy_compose_ui/app-compose-reference) — re-homed here since that
 * module is no longer part of the build. Nothing about the logic changed.
 */
object BiometricPromptHelper {

    fun isAvailable(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS

    fun enroll(activity: FragmentActivity, cipher: Cipher, onResult: (Cipher?) -> Unit) {
        show(activity, cipher, title = "Enable biometric unlock", onResult)
    }

    fun unlock(activity: FragmentActivity, cipher: Cipher, onResult: (Cipher?) -> Unit) {
        show(activity, cipher, title = "Unlock VaultKey", onResult)
    }

    private fun show(activity: FragmentActivity, cipher: Cipher, title: String, onResult: (Cipher?) -> Unit) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onResult(result.cryptoObject?.cipher)
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onResult(null)
                }
                override fun onAuthenticationFailed() {
                    // A single failed attempt — the prompt stays open for a retry.
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setNegativeButtonText("Use password instead")
            .build()
        prompt.authenticate(BiometricPrompt.CryptoObject(cipher), info)
    }
}
