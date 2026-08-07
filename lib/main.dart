import 'package:flutter/material.dart';
// Not called from anywhere in this file — imported ONLY so the compiler
// includes keyboardMain() (and everything it needs) in the same app bundle
// FlutterVaultIME.kt loads. Flutter's tree shaker keeps a
// @pragma('vm:entry-point') function from being stripped, but only if its
// file is reachable via some import chain from main.dart in the first
// place — without this import, `flutter build apk` would produce a bundle
// that's missing the keyboard entirely. See INTEGRATION.md.
// ignore: unused_import
import 'keyboard/keyboard_app.dart';
import 'screens/unlock_screen.dart';
import 'screens/vault_list_screen.dart';
import 'services/vault_channel.dart';
import 'theme.dart';

void main() {
  runApp(const VaultKeyApp());
}

class VaultKeyApp extends StatelessWidget {
  const VaultKeyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'VaultKey',
      debugShowCheckedModeBanner: false,
      theme: buildVaultKeyTheme(),
      darkTheme: buildVaultKeyDarkTheme(),
      themeMode: ThemeMode.system,
      home: const _StartupGate(),
    );
  }
}

/// Checks native vault state once at launch and routes accordingly. Screens
/// themselves navigate onward from there (e.g. UnlockScreen pushes
/// VaultListScreen on success) rather than re-checking state on every frame.
class _StartupGate extends StatefulWidget {
  const _StartupGate();

  @override
  State<_StartupGate> createState() => _StartupGateState();
}

class _StartupGateState extends State<_StartupGate> {
  late final Future<VaultState> _stateFuture = VaultChannel.instance.getVaultState();

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<VaultState>(
      future: _stateFuture,
      builder: (context, snapshot) {
        if (snapshot.hasError) {
          // A MethodChannel failure here would otherwise spin forever with no
          // way forward — surface it and offer a retry into the unlock flow.
          return Scaffold(
            body: Center(
              child: Padding(
                padding: const EdgeInsets.all(24),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    const Text(
                      "Couldn't reach the vault service.",
                      textAlign: TextAlign.center,
                    ),
                    const SizedBox(height: 12),
                    ElevatedButton(
                      onPressed: () => Navigator.of(context).pushReplacement(
                        MaterialPageRoute(builder: (_) => const UnlockScreen()),
                      ),
                      child: const Text('Continue'),
                    ),
                  ],
                ),
              ),
            ),
          );
        }
        if (!snapshot.hasData) {
          return const Scaffold(body: Center(child: CircularProgressIndicator()));
        }
        if (snapshot.data == VaultState.unlocked) {
          return const VaultListScreen();
        }
        return const UnlockScreen();
      },
    );
  }
}
