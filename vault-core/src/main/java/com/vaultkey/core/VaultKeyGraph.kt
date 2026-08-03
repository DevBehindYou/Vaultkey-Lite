package com.vaultkey.core

import android.content.Context
import com.vaultkey.core.crypto.BiometricUnlock
import com.vaultkey.core.crypto.VaultMetadataStore
import com.vaultkey.core.crypto.VaultSession
import com.vaultkey.core.data.CredentialRepository

/**
 * A deliberately tiny, hand-rolled DI container instead of Hilt/Dagger — the
 * three Android components that need this (the vault Activity, the keyboard
 * IME, and the autofill service) are all separate entry points into the same
 * process, and all three need to see the *same* VaultSession instance so
 * that unlocking in one place (say, the vault app) is visible to the others.
 *
 * IMPORTANT: this relies on the keyboard and autofill services running in
 * the app's default process (no custom android:process in the manifest).
 * VaultKeyApplication.onCreate() calls init() before any service can start,
 * since Android always creates the Application object first within a process.
 */
object VaultKeyGraph {

    private var _repository: CredentialRepository? = null
    private var _session: VaultSession? = null

    fun init(appContext: Context) {
        if (_session != null) return // already initialized — services can call this defensively
        val metadataStore = VaultMetadataStore(appContext)
        val biometricUnlock = BiometricUnlock()
        val session = VaultSession(appContext, metadataStore, biometricUnlock)
        _session = session
        _repository = CredentialRepository(session)
    }

    val session: VaultSession
        get() = _session ?: error("VaultKeyGraph.init() was never called")

    val credentialRepository: CredentialRepository
        get() = _repository ?: error("VaultKeyGraph.init() was never called")
}
