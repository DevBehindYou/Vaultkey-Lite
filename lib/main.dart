import 'package:flutter/material.dart';
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
