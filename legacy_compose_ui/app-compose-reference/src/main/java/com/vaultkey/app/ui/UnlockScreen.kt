package com.vaultkey.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vaultkey.core.VaultKeyGraph
import com.vaultkey.core.crypto.VaultState

/**
 * Handles both first-run ("choose a master password") and everyday unlock —
 * VaultSession.state tells us which one we're in. Matches SCR.01 in
 * 03-ui-ux-mockup.html.
 */
@Composable
fun UnlockScreen(onUnlocked: () -> Unit, onOfferBiometricEnrollment: () -> Unit) {
    val session = VaultKeyGraph.session
    val context = androidx.compose.ui.platform.LocalContext.current
    var isFirstRun by remember { mutableStateOf(session.state == VaultState.Uninitialized) }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (isFirstRun) "Create your master password" else "Unlock VaultKey",
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (isFirstRun) {
                "This unlocks your vault. There's no reset — if it's lost, the vault can't be recovered, on purpose."
            } else {
                "Use your master password or biometric to continue"
            },
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it; error = null },
            label = { Text("Master password") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors()
        )

        if (isFirstRun) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it; error = null },
                label = { Text("Confirm password") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                if (isFirstRun) {
                    if (password.length < 8) {
                        error = "Use at least 8 characters"
                    } else if (password != confirmPassword) {
                        error = "Passwords don't match"
                    } else {
                        session.setUpNewVault(password.toCharArray())
                        if (BiometricPromptHelper.isAvailable(context)) {
                            val activity = context as androidx.fragment.app.FragmentActivity
                            BiometricPromptHelper.enroll(activity, session.biometricEnrollCipher()) { authorizedCipher ->
                                if (authorizedCipher != null) {
                                    session.completeBiometricEnrollment(authorizedCipher)
                                }
                                // Whether or not the user completes/declines biometric
                                // enrollment here, the vault itself is already created
                                // and unlocked from setUpNewVault() above — proceed either way.
                                onOfferBiometricEnrollment()
                            }
                        } else {
                            // No biometric hardware, or hardware with nothing enrolled —
                            // skip straight to the vault rather than showing a prompt
                            // that would just fail.
                            onOfferBiometricEnrollment()
                        }
                    }
                } else {
                    if (session.unlockWithPassword(password.toCharArray())) {
                        onUnlocked()
                    } else {
                        error = "Wrong password"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text(if (isFirstRun) "Create vault" else "Unlock")
        }

        if (!isFirstRun) {
            val biometricCipher = remember { session.biometricUnlockCipher() }
            if (biometricCipher != null) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = {
                    val activity = context as androidx.fragment.app.FragmentActivity
                    BiometricPromptHelper.unlock(activity, biometricCipher) { authorizedCipher ->
                        if (authorizedCipher != null && session.unlockWithBiometricCipher(authorizedCipher)) {
                            onUnlocked()
                        } else {
                            error = "Biometric unlock failed — use your master password"
                        }
                    }
                }) {
                    Text("Use Face / Fingerprint instead")
                }
            }
        }
    }
}
