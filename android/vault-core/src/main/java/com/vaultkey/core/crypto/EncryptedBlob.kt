package com.vaultkey.core.crypto

/**
 * An IV + ciphertext pair — the value type every AES-GCM helper in this package
 * (FieldCipher, PasswordKeyDerivation, BiometricUnlock) produces and consumes,
 * and that VaultMetadataStore serialises. Originally declared at the bottom of
 * CryptoManager.kt; moved to its own file when that (otherwise unused) class was
 * removed, since this type is very much in use across the crypto and data layers.
 */
data class EncryptedBlob(val iv: ByteArray, val ciphertext: ByteArray)
