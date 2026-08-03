package com.vaultkey.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.vaultkey.app.ui.VaultKeyNavHost
import com.vaultkey.app.ui.theme.VaultKeyTheme
import com.vaultkey.core.VaultKeyGraph

/**
 * Single-activity app. FragmentActivity (rather than plain ComponentActivity)
 * because androidx.biometric.BiometricPrompt requires one — see
 * BiometricPromptHelper.kt. FLAG_SECURE excludes this screen from the system
 * recents thumbnail and screen recording/casting, per the threat-model note
 * in 02-architecture.md about shoulder-surfing/screen capture of visible
 * secrets.
 */
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )
        setContent {
            VaultKeyTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    VaultKeyNavHost()
                }
            }
        }
    }

    /**
     * Fires on every touch/key dispatch to this Activity — the single hook
     * that keeps the vault's idle auto-lock timer from expiring while the
     * user is actively using the app.
     */
    override fun onUserInteraction() {
        super.onUserInteraction()
        VaultKeyGraph.session.notifyUserActivity()
    }
}
