package com.vaultkey.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Same tokens used in 03-ui-ux-mockup.html, kept in one place so the built
// app and the design mockup never drift apart.
val Ink = Color(0xFF14151A)
val Paper = Color(0xFFECEAE6)
val Paper2 = Color(0xFFE2E0DB)
val AccentBlue = Color(0xFF2F4EEA)
val Muted = Color(0xFF87868C)
val Line = Color(0xFFD3D1CB)

private val VaultKeyLightColors = lightColorScheme(
    primary = AccentBlue,
    onPrimary = Color.White,
    background = Paper,
    surface = Color.White,
    onBackground = Ink,
    onSurface = Ink,
    secondaryContainer = Paper2,
)

private val VaultKeyDarkColors = darkColorScheme(
    primary = AccentBlue,
    onPrimary = Color.White,
    background = Ink,
    surface = Color(0xFF1E1F25),
    onBackground = Color(0xFFECEAE6),
    onSurface = Color(0xFFECEAE6),
)

@Composable
fun VaultKeyTheme(useDarkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (useDarkTheme) VaultKeyDarkColors else VaultKeyLightColors,
        typography = VaultKeyTypography,
        content = content
    )
}
