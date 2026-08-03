package com.vaultkey.core.crypto

import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypts individual credential fields (username/password/notes) using the
 * session's raw database key directly — this is defense-in-depth *inside*
 * the already-SQLCipher-encrypted database file, so that a memory dump or a
 * bug that leaks a raw DB row still doesn't hand over plaintext secrets.
 *
 * Deliberately NOT Keystore-backed: the whole point is this can run silently,
 * many times per screen, without provoking a biometric prompt per field.
 * The raw key itself is only ever reachable after a real unlock (password or
 * biometric) via VaultSession.
 */
class FieldCipher(rawKeyBytes: ByteArray) {

    private val key = SecretKeySpec(rawKeyBytes, "AES")

    fun encrypt(plaintext: ByteArray): EncryptedBlob {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return EncryptedBlob(iv = cipher.iv, ciphertext = cipher.doFinal(plaintext))
    }

    fun decrypt(blob: EncryptedBlob): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, blob.iv))
        return cipher.doFinal(blob.ciphertext)
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
    }
}
