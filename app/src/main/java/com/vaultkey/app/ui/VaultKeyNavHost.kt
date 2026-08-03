package com.vaultkey.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.vaultkey.core.VaultKeyGraph
import com.vaultkey.core.crypto.VaultState

private object Routes {
    const val UNLOCK = "unlock"
    const val VAULT_LIST = "vault_list"
    const val ADD_CREDENTIAL = "add_credential"
    const val SETTINGS = "settings"
}

@Composable
fun VaultKeyNavHost() {
    val navController = rememberNavController()
    var startDestination by remember {
        mutableStateOf(
            if (VaultKeyGraph.session.state == VaultState.Unlocked) Routes.VAULT_LIST else Routes.UNLOCK
        )
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.UNLOCK) {
            UnlockScreen(
                onUnlocked = {
                    navController.navigate(Routes.VAULT_LIST) {
                        popUpTo(Routes.UNLOCK) { inclusive = true }
                    }
                },
                onOfferBiometricEnrollment = {
                    // A full build would show a "want to add biometric unlock?"
                    // dialog here before continuing; skipping straight to the
                    // vault keeps this reference navigation graph readable.
                    navController.navigate(Routes.VAULT_LIST) {
                        popUpTo(Routes.UNLOCK) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.VAULT_LIST) {
            VaultListScreen(
                onAddCredential = { navController.navigate(Routes.ADD_CREDENTIAL) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }
        composable(Routes.ADD_CREDENTIAL) {
            AddEditCredentialScreen(
                onSaved = { navController.popBackStack() },
                onCancel = { navController.popBackStack() }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen()
        }
    }
}
