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
    private val autoLockAfterMillis: Long = 30_000L
) {
    // @Volatile: the auto-lock timer flips these to null on Dispatchers.Default
    // while the vault UI / keyboard / autofill read them on other threads —
    // without it, a stale non-null read could hand out a half-torn-down session.
    @Volatile private var rawDbKey: ByteArray? = null
    @Volatile private var database: VaultDatabase? = null
    @Volatile private var fieldCipher: FieldCipher? = null
    private var autoLockJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    val state: VaultState
        get() = when {
            !metadataStore.isInitialized -> VaultState.Uninitialized
            rawDbKey == null -> VaultState.Locked
            else -> VaultState.Unlocked
        }

    /** First-run setup: choose a master password, generate a brand-new random DB key. */
    fun setUpNewVault(masterPassword: CharArray) {
        val salt = PasswordKeyDerivation.generateSalt()
        val newDbKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val wrapped = PasswordKeyDerivation.wrapDbKey(masterPassword, salt, newDbKey)
        metadataStore.savePasswordUnlock(salt, wrapped)
        activate(newDbKey)
        Arrays.fill(masterPassword, '0')
    }

    /** Returns true if the password was correct and the vault is now unlocked. */
    fun unlockWithPassword(masterPassword: CharArray): Boolean {
        return try {
            val salt = metadataStore.loadSalt()
            val wrapped = metadataStore.loadWrappedPasswordKey()
            val dbKey = PasswordKeyDerivation.unwrapDbKey(masterPassword, salt, wrapped)
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

    fun currentDatabase(): VaultDatabase = database ?: error("Vault is locked")
    fun currentFieldCipher(): FieldCipher = fieldCipher ?: error("Vault is locked")

    fun lock() {
        rawDbKey?.let { Arrays.fill(it, 0) }
        rawDbKey = null
        database?.close()
        database = null
        fieldCipher = null
        autoLockJob?.cancel()
    }

    /** Call on every user interaction (keystroke, tap) to push the auto-lock timer back. */
    fun notifyUserActivity() {
        autoLockJob?.cancel()
        if (state == VaultState.Unlocked) {
            autoLockJob = scope.launch {
                delay(autoLockAfterMillis)
                lock()
            }
        }
    }

    private fun activate(dbKey: ByteArray) {
        rawDbKey = dbKey
        database = VaultDatabase.build(appContext, dbKey)
        fieldCipher = FieldCipher(dbKey)
        notifyUserActivity()
    }
}
