package com.vaultkey.app.ui

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import javax.crypto.Cipher

/**
 * Thin wrapper so composables can trigger a real BiometricPrompt round-trip
 * without each screen re-implementing the callback boilerplate. The Cipher
 * that comes back in onAuthenticationSucceeded is what proves a genuine
 * biometric check happened — see BiometricUnlock.kt's class doc.
 */
object BiometricPromptHelper {

    /**
     * Check this BEFORE offering biometric enrollment or unlock anywhere in
     * the UI — a device with no biometric hardware, or hardware but nothing
     * enrolled, will otherwise fail confusingly (or crash) when a Cipher-based
     * BiometricPrompt.authenticate() call is made against it.
     */
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
                    // A single failed attempt (e.g. unrecognized fingerprint) —
                    // the prompt stays open for a retry, nothing to do here.
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
