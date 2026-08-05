package com.vaultkey.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.vaultkey.core.VaultKeyGraph
import com.vaultkey.core.data.MatchType
import kotlinx.coroutines.launch

/**
 * Matches SCR.04 in 03-ui-ux-mockup.html. Website domain and app package are
 * two separate, both-optional fields rather than one field Claude guesses
 * the type of — one credential can carry both matches at once (e.g. GitHub's
 * app AND github.com), exactly as modeled by CredentialMatchEntity.
 */
@Composable
fun AddEditCredentialScreen(onSaved: () -> Unit, onCancel: () -> Unit) {
    var label by remember { mutableStateOf("") }
    var webDomain by remember { mutableStateOf("") }     // e.g. "github.com"
    var packageName by remember { mutableStateOf("") }   // e.g. "com.github.android"
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add login") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Filled.Close, contentDescription = "Cancel")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            OutlinedTextField(label, { label = it }, label = { Text("Label (e.g. GitHub)") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(webDomain, { webDomain = it }, label = { Text("Website domain (optional)") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(packageName, { packageName = it }, label = { Text("App package name (optional)") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(username, { username = it }, label = { Text("Username / email") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                password, { password = it },
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(notes, { notes = it }, label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth())

            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it)
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    if (label.isBlank() || (webDomain.isBlank() && packageName.isBlank())) {
                        error = "Add a label and at least one of website domain / app package"
                        return@Button
                    }
                    val matches = buildList {
                        if (webDomain.isNotBlank()) add(MatchType.WEB_DOMAIN to webDomain.trim())
                        if (packageName.isNotBlank()) add(MatchType.PACKAGE_NAME to packageName.trim())
                    }
                    scope.launch {
                        VaultKeyGraph.credentialRepository.addCredential(
                            label = label.trim(),
                            username = username,
                            password = password,
                            notes = notes.ifBlank { null },
                            matches = matches
                        )
                        onSaved()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("Save to Vault")
            }
        }
    }
}
