package com.vaultkey.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.annotation.NonNull
import com.vaultkey.core.VaultKeyGraph
import com.vaultkey.core.crypto.VaultState
import com.vaultkey.core.data.MatchType
import io.flutter.embedding.android.FlutterFragmentActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * FlutterFragmentActivity (not the more common FlutterActivity) specifically
 * because androidx.biometric.BiometricPrompt requires a FragmentActivity —
 * same reason the pre-Flutter Compose MainActivity used FragmentActivity too
 * (see legacy_compose_ui/app-compose-reference for that version).
 *
 * All actual vault logic still lives in vault-core (VaultSession,
 * CredentialRepository) — this class is purely a translation layer between
 * Dart method calls and those existing Kotlin APIs. See DATA_FLOW.md for
 * how this fits into the overall save/unlock/suggest flows, and
 * INTEGRATION.md for why VaultKeyGraph is safe to call directly here (same
 * process as the keyboard and autofill service).
 */
class MainActivity : FlutterFragmentActivity() {

    private val activityScope = CoroutineScope(Dispatchers.Main)
    private val channelName = "com.vaultkey.app/vault"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Carried over from the pre-Flutter Compose MainActivity — this
        // screen shows saved credentials, so it's excluded from the system
        // recents thumbnail and from screen recording/casting. This was
        // dropped by accident during the initial Flutter migration pass and
        // re-added on review — see UX_UI_DESIGN.md's mobile-optimization notes.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
    }

    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        VaultKeyGraph.init(applicationContext) // no-op if already initialized elsewhere in this process

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, channelName).setMethodCallHandler { call, result ->
            // Any interaction with the vault UI counts as activity — push the
            // idle auto-lock timer back so the vault never locks mid-use (e.g.
            // while the user is part-way through adding a credential). No-op
            // when the vault isn't unlocked, so it's safe to call for every
            // call, including the pre-unlock ones.
            VaultKeyGraph.session.notifyUserActivity()
            when (call.method) {
                "getVaultState" -> result.success(vaultStateName())

                "createVault" -> {
                    val password = call.argument<String>("password").orEmpty()
                    VaultKeyGraph.session.setUpNewVault(password.toCharArray())
                    result.success(true)
                }

                "unlockWithPassword" -> {
                    val password = call.argument<String>("password").orEmpty()
                    result.success(VaultKeyGraph.session.unlockWithPassword(password.toCharArray()))
                }

                "lock" -> {
                    VaultKeyGraph.session.lock()
                    result.success(null)
                }

                "isBiometricAvailable" -> result.success(BiometricPromptHelper.isAvailable(this))

                "isBiometricEnabled" -> result.success(VaultKeyGraph.session.biometricUnlockCipher() != null)

                "enrollBiometric" -> {
                    if (!BiometricPromptHelper.isAvailable(this)) {
                        result.success(false)
                    } else {
                        val cipher = VaultKeyGraph.session.biometricEnrollCipher()
                        BiometricPromptHelper.enroll(this, cipher) { authorizedCipher ->
                            if (authorizedCipher != null) {
                                VaultKeyGraph.session.completeBiometricEnrollment(authorizedCipher)
                                result.success(true)
                            } else {
                                result.success(false)
                            }
                        }
                    }
                }

                "unlockWithBiometric" -> {
                    val cipher = VaultKeyGraph.session.biometricUnlockCipher()
                    if (cipher == null) {
                        result.success(false)
                    } else {
                        BiometricPromptHelper.unlock(this, cipher) { authorizedCipher ->
                            val success = authorizedCipher != null &&
                                VaultKeyGraph.session.unlockWithBiometricCipher(authorizedCipher)
                            result.success(success)
                        }
                    }
                }

                "disableBiometric" -> {
                    VaultKeyGraph.session.disableBiometricUnlock()
                    result.success(null)
                }

                "getCredentialSummaries" -> runOnVault(result) {
                    val summaries = VaultKeyGraph.credentialRepository.getAllSummaries()
                    result.success(summaries.map { mapOf("id" to it.id, "label" to it.label, "username" to it.username) })
                }

                "getCredentialDetail" -> runOnVault(result) {
                    val id = call.argument<String>("id")
                    val credential = id?.let { VaultKeyGraph.credentialRepository.getDetailById(it) }
                    if (credential != null) VaultKeyGraph.credentialRepository.markUsed(credential.id)
                    result.success(
                        credential?.let {
                            mapOf(
                                "id" to it.id, "label" to it.label, "username" to it.username,
                                "password" to it.password, "notes" to it.notes,
                                "webDomain" to it.webDomain, "packageName" to it.packageName
                            )
                        }
                    )
                }

                "addCredential" -> runOnVault(result) {
                    val webDomain = call.argument<String>("webDomain").orEmpty()
                    val packageName = call.argument<String>("packageName").orEmpty()
                    val matches = buildList {
                        if (webDomain.isNotBlank()) add(MatchType.WEB_DOMAIN to webDomain)
                        if (packageName.isNotBlank()) add(MatchType.PACKAGE_NAME to packageName)
                    }
                    VaultKeyGraph.credentialRepository.addCredential(
                        label = call.argument<String>("label").orEmpty(),
                        username = call.argument<String>("username").orEmpty(),
                        password = call.argument<String>("password").orEmpty(),
                        notes = call.argument<String>("notes")?.ifBlank { null },
                        matches = matches
                    )
                    result.success(null)
                }

                "updateCredential" -> runOnVault(result) {
                    val webDomain = call.argument<String>("webDomain").orEmpty()
                    val packageName = call.argument<String>("packageName").orEmpty()
                    val matches = buildList {
                        if (webDomain.isNotBlank()) add(MatchType.WEB_DOMAIN to webDomain)
                        if (packageName.isNotBlank()) add(MatchType.PACKAGE_NAME to packageName)
                    }
                    VaultKeyGraph.credentialRepository.updateCredential(
                        id = call.argument<String>("id").orEmpty(),
                        label = call.argument<String>("label").orEmpty(),
                        username = call.argument<String>("username").orEmpty(),
                        password = call.argument<String>("password").orEmpty(),
                        notes = call.argument<String>("notes")?.ifBlank { null },
                        matches = matches
                    )
                    result.success(null)
                }

                "deleteCredential" -> runOnVault(result) {
                    VaultKeyGraph.credentialRepository.deleteCredential(call.argument<String>("id").orEmpty())
                    result.success(null)
                }

                "openImeSettings" -> {
                    startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                    result.success(null)
                }

                "openAutofillSettings" -> {
                    val intent = Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                    result.success(null)
                }

                else -> result.notImplemented()
            }
        }
    }

    /**
     * Runs a suspend vault operation and, crucially, converts any thrown
     * exception into a MethodChannel error reply. Without this a repository
     * failure (e.g. the vault locked mid-call) would abandon the coroutine and
     * leave the Dart `await` hanging forever. The block is responsible for its
     * own `result.success(...)` on the happy path.
     */
    private fun runOnVault(result: MethodChannel.Result, block: suspend () -> Unit) {
        activityScope.launch {
            try {
                block()
            } catch (e: Exception) {
                result.error("vault_error", e.message, null)
            }
        }
    }

    private fun vaultStateName(): String = when (VaultKeyGraph.session.state) {
        VaultState.Uninitialized -> "uninitialized"
        VaultState.Locked -> "locked"
        VaultState.Unlocked -> "unlocked"
    }
}
