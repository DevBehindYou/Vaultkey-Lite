package com.vaultkey.core.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The optional "something you are" unlock path. This key lives in the
 * Keystore with setUserAuthenticationRequired(true), so the OS itself
 * enforces that a successful BiometricPrompt happened immediately before
 * the key can be used — the app never sees or checks the fingerprint/face
 * data itself, only the resulting authorized Cipher.
 *
 * If biometrics are ever revoked/re-enrolled, Android invalidates this key
 * automatically; the caller should catch KeyPermanentlyInvalidatedException
 * and fall back to prompting for the master password, then re-enroll.
 */
class BiometricUnlock {

    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    fun ensureBiometricKeyExists() {
        if (keyStore.containsAlias(BIOMETRIC_KEY_ALIAS)) return
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            BIOMETRIC_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
            .build()
        keyGenerator.init(spec)
        keyGenerator.generateKey()
    }

    fun removeBiometricKey() {
        if (keyStore.containsAlias(BIOMETRIC_KEY_ALIAS)) keyStore.deleteEntry(BIOMETRIC_KEY_ALIAS)
    }

    /** Pass this Cipher into BiometricPrompt.CryptoObject for the *wrap* step (first-time enrollment). */
    fun encryptCipher(): Cipher {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getBiometricKey())
        return cipher
    }

    /** Pass this Cipher into BiometricPrompt.CryptoObject for the *unlock* step. */
    fun decryptCipher(iv: ByteArray): Cipher {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getBiometricKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return cipher
    }

    /**
     * Call from the BiometricPrompt.AuthenticationCallback#onAuthenticationSucceeded
     * result's cryptoObject.cipher — this is what actually proves a real
     * biometric check happened, rather than the app just trusting a callback fired.
     */
    fun wrapDbKey(authorizedEncryptCipher: Cipher, dbKey: ByteArray): EncryptedBlob =
        EncryptedBlob(iv = authorizedEncryptCipher.iv, ciphertext = authorizedEncryptCipher.doFinal(dbKey))

    fun unwrapDbKey(authorizedDecryptCipher: Cipher, blob: EncryptedBlob): ByteArray =
        authorizedDecryptCipher.doFinal(blob.ciphertext)

    private fun getBiometricKey(): SecretKey =
        (keyStore.getEntry(BIOMETRIC_KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val BIOMETRIC_KEY_ALIAS = "vaultkey_biometric_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
    }
}
