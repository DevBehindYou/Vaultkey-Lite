package com.vaultkey.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.vaultkey.core.VaultKeyGraph

/** Matches SCR.02 in 03-ui-ux-mockup.html. */
@OptIn(ExperimentalMaterial3Api::class) // material3 TopAppBar is still experimental
@Composable
fun VaultListScreen(onAddCredential: () -> Unit, onOpenSettings: () -> Unit) {
    val credentials by remember { VaultKeyGraph.credentialRepository.observeAll() }
        .collectAsState(initial = emptyList())
    var query by remember { mutableStateOf("") }

    val filtered = credentials.filter { it.label.contains(query, ignoreCase = true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vault") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddCredential) {
                Icon(Icons.Filled.Add, contentDescription = "Add login")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search saved logins") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            )
            if (filtered.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        if (credentials.isEmpty()) {
                            "No saved logins yet. Tap + to add your first one."
                        } else {
                            "No matches for \"$query\""
                        }
                    )
                }
            } else {
                LazyColumn {
                    items(filtered) { credential ->
                        ListItem(
                            headlineContent = { Text(credential.label) },
                            supportingContent = { Text(credential.username) }
                        )
                    }
                }
            }
        }
    }
}
