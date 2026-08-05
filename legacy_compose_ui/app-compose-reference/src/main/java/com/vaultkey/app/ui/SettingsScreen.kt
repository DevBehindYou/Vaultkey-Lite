package com.vaultkey.app.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.vaultkey.core.VaultKeyGraph

/** Matches SCR.05 in 03-ui-ux-mockup.html. */
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    var biometricEnabled by remember { mutableStateOf(VaultKeyGraph.session.biometricUnlockCipher() != null) }
    var unavailableMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ListItem(
                headlineContent = { Text("VaultKey Keyboard") },
                supportingContent = { Text("Set as your default input method") },
                modifier = Modifier.clickableRow {
                    context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                }
            )
            ListItem(
                headlineContent = { Text("Autofill service") },
                supportingContent = { Text("Needed for browser/website matching") },
                modifier = Modifier.clickableRow {
                    val intent = Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE)
                    intent.data = Uri.parse("package:${context.packageName}")
                    context.startActivity(intent)
                }
            )
            ListItem(
                headlineContent = { Text("Biometric unlock") },
                supportingContent = { Text("Face / fingerprint instead of typing your master password") },
                trailingContent = {
                    Switch(
                        checked = biometricEnabled,
                        onCheckedChange = { enabled ->
                            if (!enabled) {
                                VaultKeyGraph.session.disableBiometricUnlock()
                                biometricEnabled = false
                            } else if (BiometricPromptHelper.isAvailable(context)) {
                                val activity = context as androidx.fragment.app.FragmentActivity
                                val cipher = VaultKeyGraph.session.biometricEnrollCipher()
                                BiometricPromptHelper.enroll(activity, cipher) { authorizedCipher ->
                                    if (authorizedCipher != null) {
                                        VaultKeyGraph.session.completeBiometricEnrollment(authorizedCipher)
                                        biometricEnabled = true
                                    }
                                }
                            } else {
                                unavailableMessage = "No fingerprint/face unlock is set up on this device"
                            }
                        }
                    )
                }
            )
            unavailableMessage?.let {
                ListItem(headlineContent = { Text(it) })
            }
        }
    }
}

private fun Modifier.clickableRow(onClick: () -> Unit): Modifier =
    this.then(androidx.compose.foundation.clickable(onClick = onClick))
