package com.vaultkey.core.crypto

import android.content.Context
import android.util.Base64

/**
 * Everything here is either a salt, an iteration count, or an already-encrypted
 * blob — safe to store in a plain (non-Encrypted) SharedPreferences file,
 * because none of it is useful without either the master password or a live
 * biometric-authorized Cipher. This is standard envelope-encryption practice:
 * the wrapping metadata isn't itself a secret.
 */
class VaultMetadataStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val isInitialized: Boolean
        get() = prefs.contains(KEY_SALT) && prefs.contains(KEY_WRAPPED_PASSWORD)

    fun savePasswordUnlock(salt: ByteArray, wrapped: EncryptedBlob) {
        prefs.edit()
            .putString(KEY_SALT, salt.toB64())
            .putString(KEY_WRAPPED_PASSWORD, wrapped.toB64())
            .apply()
    }

    fun loadSalt(): ByteArray = prefs.getString(KEY_SALT, null)!!.fromB64()

    fun loadWrappedPasswordKey(): EncryptedBlob = prefs.getString(KEY_WRAPPED_PASSWORD, null)!!.toBlob()

    fun saveBiometricUnlock(wrapped: EncryptedBlob) {
        prefs.edit().putString(KEY_WRAPPED_BIOMETRIC, wrapped.toB64()).apply()
    }

    fun loadWrappedBiometricKey(): EncryptedBlob? =
        prefs.getString(KEY_WRAPPED_BIOMETRIC, null)?.toBlob()

    fun clearBiometricUnlock() {
        prefs.edit().remove(KEY_WRAPPED_BIOMETRIC).apply()
    }

    // --- tiny helpers: EncryptedBlob <-> "ivB64:ciphertextB64" ---

    private fun ByteArray.toB64(): String = Base64.encodeToString(this, Base64.NO_WRAP)
    private fun String.fromB64(): ByteArray = Base64.decode(this, Base64.NO_WRAP)
    private fun EncryptedBlob.toB64(): String = "${iv.toB64()}:${ciphertext.toB64()}"
    private fun String.toBlob(): EncryptedBlob {
        val (ivPart, cipherPart) = split(":", limit = 2)
        return EncryptedBlob(iv = ivPart.fromB64(), ciphertext = cipherPart.fromB64())
    }

    companion object {
        private const val PREFS_NAME = "vaultkey_metadata"
        private const val KEY_SALT = "pbkdf2_salt"
        private const val KEY_WRAPPED_PASSWORD = "wrapped_db_key_password"
        private const val KEY_WRAPPED_BIOMETRIC = "wrapped_db_key_biometric"
    }
}
