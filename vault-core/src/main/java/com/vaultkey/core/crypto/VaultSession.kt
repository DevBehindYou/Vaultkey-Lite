package com.vaultkey.core.crypto

import android.content.Context
import com.vaultkey.core.data.VaultDatabase
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.Cipher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed class VaultState {
    object Uninitialized : VaultState()   // first run — no master password set yet
    object Locked : VaultState()
    object Unlocked : VaultState()
}

/**
 * Owns the ONE copy of the raw, unwrapped database key that exists in memory
 * at any time. Everything downstream (VaultDatabase, FieldCipher) is derived
 * from this session, never re-reads the wrapped forms directly, and is
 * dropped the moment the vault locks.
 */
class VaultSession(
    private val appContext: Context,
    private val metadataStore: VaultMetadataStore,
    private val biometricUnlock: BiometricUnlock,
    private val cryptoManager: CryptoManager = CryptoManager(),
    private val autoLockAfterMillis: Long = 5 * 60 * 1000L
) {
    private var rawDbKey: ByteArray? = null
    private var database: VaultDatabase? = null
    private var fieldCipher: FieldCipher? = null
    private var autoLockJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    // Guards every read/write of the mutable session state below. Auto-lock
    // runs on a background coroutine, so activate()/lock() and the accessors
    // must not race it (a lock() mid-read would otherwise tear down the DB
    // out from under an in-flight query).
    private val stateLock = Any()

    val state: VaultState
        get() = synchronized(stateLock) {
            when {
                !metadataStore.isInitialized -> VaultState.Uninitialized
                rawDbKey == null -> VaultState.Locked
                else -> VaultState.Unlocked
            }
        }

    /** First-run setup: choose a master password, generate a brand-new random DB key. */
    fun setUpNewVault(masterPassword: CharArray) {
        try {
            val salt = PasswordKeyDerivation.generateSalt()
            val newDbKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
            // Envelope 1: wrap the DB key with a KEK derived from the master password.
            val passwordWrapped = PasswordKeyDerivation.wrapDbKey(masterPassword, salt, newDbKey)
            // Envelope 2: wrap that blob again with the hardware-backed Keystore
            // key, so the value persisted to disk is undecryptable off-device —
            // an attacker who copies the prefs file can't even begin an offline
            // brute-force of the master password without this device's TEE.
            cryptoManager.ensureMasterKeyExists()
            val deviceWrapped = cryptoManager.wrap(passwordWrapped.toBytes())
            metadataStore.savePasswordUnlock(salt, deviceWrapped)
            activate(newDbKey)
        } finally {
            Arrays.fill(masterPassword, '0')
        }
    }

    /** Returns true if the password was correct and the vault is now unlocked. */
    fun unlockWithPassword(masterPassword: CharArray): Boolean {
        return try {
            val salt = metadataStore.loadSalt()
            val deviceWrapped = metadataStore.loadWrappedPasswordKey()
            // Peel the Keystore layer first (requires this device), then the
            // password layer (requires the correct master password).
            val passwordWrapped = EncryptedBlob.fromBytes(cryptoManager.unwrap(deviceWrapped))
            val dbKey = PasswordKeyDerivation.unwrapDbKey(masterPassword, salt, passwordWrapped)
            activate(dbKey)
            true
        } catch (e: Exception) {
            false // wrong password -> AEADBadTagException, caught here deliberately
        } finally {
            Arrays.fill(masterPassword, '0')
        }
    }

    /** Cipher to hand to BiometricPrompt for a fresh enrollment (after password unlock). */
    fun biometricEnrollCipher(): Cipher {
        biometricUnlock.ensureBiometricKeyExists()
        return biometricUnlock.encryptCipher()
    }

    fun completeBiometricEnrollment(authorizedCipher: Cipher) {
        val dbKey = rawDbKey ?: error("Vault must already be unlocked to enroll biometrics")
        val wrapped = biometricUnlock.wrapDbKey(authorizedCipher, dbKey)
        metadataStore.saveBiometricUnlock(wrapped)
    }

    /** Cipher to hand to BiometricPrompt for everyday unlock. Null if biometrics aren't enrolled. */
    fun biometricUnlockCipher(): Cipher? {
        val wrapped = metadataStore.loadWrappedBiometricKey() ?: return null
        return biometricUnlock.decryptCipher(wrapped.iv)
    }

    fun unlockWithBiometricCipher(authorizedCipher: Cipher): Boolean {
        val wrapped = metadataStore.loadWrappedBiometricKey() ?: return false
        return try {
            val dbKey = biometricUnlock.unwrapDbKey(authorizedCipher, wrapped)
            activate(dbKey)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun disableBiometricUnlock() {
        biometricUnlock.removeBiometricKey()
        metadataStore.clearBiometricUnlock()
    }

    fun currentDatabase(): VaultDatabase = synchronized(stateLock) { database ?: error("Vault is locked") }
    fun currentFieldCipher(): FieldCipher = synchronized(stateLock) { fieldCipher ?: error("Vault is locked") }

    fun lock() {
        synchronized(stateLock) {
            rawDbKey?.let { Arrays.fill(it, 0) }
            rawDbKey = null
            database?.close()
            database = null
            fieldCipher = null
            autoLockJob?.cancel()
            autoLockJob = null
        }
    }

    /** Call on every user interaction (keystroke, tap) to push the auto-lock timer back. */
    fun notifyUserActivity() {
        synchronized(stateLock) {
            autoLockJob?.cancel()
            if (rawDbKey != null) {
                autoLockJob = scope.launch {
                    delay(autoLockAfterMillis)
                    lock()
                }
            }
        }
    }

    private fun activate(dbKey: ByteArray) {
        synchronized(stateLock) {
            rawDbKey = dbKey
            // SecretKeySpec copies the bytes, so FieldCipher is unaffected by
            // later zeroing of dbKey/rawDbKey.
            fieldCipher = FieldCipher(dbKey)
            // Hand SQLCipher its OWN copy: SupportOpenHelperFactory zeroes the
            // passphrase after opening the DB, which would otherwise wipe our
            // rawDbKey (needed later for biometric enrollment).
            database = VaultDatabase.build(appContext, dbKey.copyOf())
        }
        notifyUserActivity()
    }
}
