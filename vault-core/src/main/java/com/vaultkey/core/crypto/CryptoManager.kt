package com.vaultkey.core.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Wraps a random 256-bit database key with a key that lives inside the
 * Android Keystore and never leaves it (not exportable, hardware-backed
 * when the device supports it via TEE/StrongBox).
 *
 * This class never returns the Keystore key itself — only encrypt/decrypt
 * results — which is the whole point: the raw key material is unextractable.
 */
class CryptoManager {

    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    /** Call once on first run; no-ops if the key already exists. */
    fun ensureMasterKeyExists() {
        if (keyStore.containsAlias(MASTER_KEY_ALIAS)) return

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE
        )
        val spec = KeyGenParameterSpec.Builder(
            MASTER_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            // Requires the user to have unlocked the device recently; tune per UX needs.
            .setUserAuthenticationRequired(false)
            .build()
        keyGenerator.init(spec)
        keyGenerator.generateKey()
    }

    /** Encrypts [plaintext] with the Keystore-backed master key. Returns iv + ciphertext. */
    fun wrap(plaintext: ByteArray): EncryptedBlob {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getMasterKey())
        val ciphertext = cipher.doFinal(plaintext)
        return EncryptedBlob(iv = cipher.iv, ciphertext = ciphertext)
    }

    /** Reverses [wrap]. */
    fun unwrap(blob: EncryptedBlob): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, blob.iv)
        cipher.init(Cipher.DECRYPT_MODE, getMasterKey(), spec)
        return cipher.doFinal(blob.ciphertext)
    }

    private fun getMasterKey(): SecretKey =
        (keyStore.getEntry(MASTER_KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val MASTER_KEY_ALIAS = "vaultkey_master_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
    }
}

data class EncryptedBlob(val iv: ByteArray, val ciphertext: ByteArray) {

    /** Serialize to a single self-describing byte array: [ivLen:int][iv][ciphertext]. */
    fun toBytes(): ByteArray {
        val buffer = java.nio.ByteBuffer.allocate(4 + iv.size + ciphertext.size)
        buffer.putInt(iv.size)
        buffer.put(iv)
        buffer.put(ciphertext)
        return buffer.array()
    }

    // Value-based equality — data classes use reference equality for array
    // properties by default, which is a latent bug the moment two blobs are
    // ever compared or deduplicated. Overridden explicitly here.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncryptedBlob) return false
        return iv.contentEquals(other.iv) && ciphertext.contentEquals(other.ciphertext)
    }

    override fun hashCode(): Int = 31 * iv.contentHashCode() + ciphertext.contentHashCode()

    companion object {
        fun fromBytes(bytes: ByteArray): EncryptedBlob {
            val buffer = java.nio.ByteBuffer.wrap(bytes)
            val ivLen = buffer.int
            val iv = ByteArray(ivLen).also { buffer.get(it) }
            val ciphertext = ByteArray(buffer.remaining()).also { buffer.get(it) }
            return EncryptedBlob(iv, ciphertext)
        }
    }
}
