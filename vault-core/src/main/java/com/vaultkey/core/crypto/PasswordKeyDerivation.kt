package com.vaultkey.core.crypto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Turns the user's master password into a key-encryption-key (KEK) that wraps
 * the real, random database key. This is intentionally pure software crypto
 * (no Keystore involvement) — it's the "something you know" unlock path,
 * separate from the optional "something you are" biometric path in
 * BiometricUnlock.kt. Both paths independently wrap the *same* underlying
 * database key so either one can recover it.
 */
object PasswordKeyDerivation {

    private const val ITERATIONS = 210_000 // OWASP 2023+ guidance floor for PBKDF2-HMAC-SHA256
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_LENGTH_BYTES = 16
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128

    fun generateSalt(): ByteArray = ByteArray(SALT_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }

    private fun deriveKek(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(keyBytes, "AES")
    }

    /** Wraps [dbKey] (the real, random 32-byte database key) with a KEK derived from [password]. */
    fun wrapDbKey(password: CharArray, salt: ByteArray, dbKey: ByteArray): EncryptedBlob {
        val kek = deriveKek(password, salt)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, kek)
        return EncryptedBlob(iv = cipher.iv, ciphertext = cipher.doFinal(dbKey))
    }

    /** Reverses [wrapDbKey]. Throws (via AEADBadTagException) if the password is wrong. */
    fun unwrapDbKey(password: CharArray, salt: ByteArray, blob: EncryptedBlob): ByteArray {
        val kek = deriveKek(password, salt)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, kek, GCMParameterSpec(GCM_TAG_LENGTH_BITS, blob.iv))
        return cipher.doFinal(blob.ciphertext)
    }

    const val iterations = ITERATIONS
}
