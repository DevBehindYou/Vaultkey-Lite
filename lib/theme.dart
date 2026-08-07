import 'package:flutter/material.dart';

/// Same tokens used in 03-ui-ux-mockup.html and the pre-Flutter Compose
/// theme (legacy_compose_ui/app-compose-reference/.../theme/Theme.kt) — kept
/// identical on purpose so the rewrite didn't quietly drift the brand. The
/// `dark*` tokens are new, added so the app has a real dark theme (it was
/// light-only before) that follows the system setting.
class VaultKeyColors {
  static const ink = Color(0xFF14151A);
  static const paper = Color(0xFFECEAE6);
  static const paper2 = Color(0xFFE2E0DB);
  static const accentBlue = Color(0xFF2F4EEA);
  static const muted = Color(0xFF87868C);
  static const line = Color(0xFFD3D1CB);

  static const darkBg = Color(0xFF14151A);
  static const darkSurface = Color(0xFF1F2028);
  static const darkOnSurface = Color(0xFFECEAE6);
}

ThemeData buildVaultKeyTheme() {
  final colorScheme = ColorScheme.fromSeed(
    seedColor: VaultKeyColors.accentBlue,
    primary: VaultKeyColors.accentBlue,
    onPrimary: Colors.white,
    surface: Colors.white,
    onSurface: VaultKeyColors.ink,
  );
  return _base(colorScheme, scaffold: VaultKeyColors.paper, appBar: VaultKeyColors.paper);
}

ThemeData buildVaultKeyDarkTheme() {
  final colorScheme = ColorScheme.fromSeed(
    seedColor: VaultKeyColors.accentBlue,
    brightness: Brightness.dark,
    primary: VaultKeyColors.accentBlue,
    onPrimary: Colors.white,
    surface: VaultKeyColors.darkSurface,
    onSurface: VaultKeyColors.darkOnSurface,
  );
  return _base(colorScheme, scaffold: VaultKeyColors.darkBg, appBar: VaultKeyColors.darkBg);
}

/// Shared button/input/appbar styling so light and dark stay in lockstep and
/// only their color scheme differs.
ThemeData _base(ColorScheme colorScheme, {required Color scaffold, required Color appBar}) {
  return ThemeData(
    useMaterial3: true,
    colorScheme: colorScheme,
    scaffoldBackgroundColor: scaffold,
    appBarTheme: AppBarTheme(
      backgroundColor: appBar,
      foregroundColor: colorScheme.onSurface,
      elevation: 0,
    ),
    elevatedButtonTheme: ElevatedButtonThemeData(
      style: ElevatedButton.styleFrom(
        backgroundColor: VaultKeyColors.accentBlue,
        foregroundColor: Colors.white,
        minimumSize: const Size.fromHeight(48),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
      ),
    ),
    inputDecorationTheme: InputDecorationTheme(
      border: OutlineInputBorder(borderRadius: BorderRadius.circular(10)),
      filled: false,
    ),
  );
}
