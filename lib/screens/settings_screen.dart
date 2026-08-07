import 'package:flutter/material.dart';
import '../services/vault_channel.dart';
import 'unlock_screen.dart';

/// Matches SCR.05.
class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  final _channel = VaultChannel.instance;
  bool _biometricEnabled = false;
  bool _biometricAvailable = false;
  String? _message;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final enabled = await _channel.isBiometricEnabled();
      final available = await _channel.isBiometricAvailable();
      if (!mounted) return;
      setState(() {
        _biometricEnabled = enabled;
        _biometricAvailable = available;
      });
    } catch (e) {
      if (mounted) setState(() => _message = 'Could not read biometric state');
    }
  }

  Future<void> _onBiometricToggle(bool value) async {
    if (!value) {
      await _channel.disableBiometric();
      setState(() => _biometricEnabled = false);
      return;
    }
    if (!_biometricAvailable) {
      setState(() => _message = 'No fingerprint/face unlock is set up on this device');
      return;
    }
    final enrolled = await _channel.enrollBiometric();
    setState(() {
      _biometricEnabled = enrolled;
      _message = enrolled ? null : 'Biometric setup was cancelled';
    });
  }

  Future<void> _lockNow() async {
    await _channel.lock();
    if (!mounted) return;
    Navigator.of(context).pushAndRemoveUntil(
      MaterialPageRoute(builder: (_) => const UnlockScreen()),
      (route) => false,
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Settings')),
      body: ListView(
        children: [
          ListTile(
            leading: const Icon(Icons.keyboard_outlined),
            title: const Text('VaultKey Keyboard'),
            subtitle: const Text('Set as your default input method'),
            onTap: _channel.openImeSettings,
          ),
          ListTile(
            leading: const Icon(Icons.password_outlined),
            title: const Text('Autofill service'),
            subtitle: const Text('Needed for browser/website matching'),
            onTap: _channel.openAutofillSettings,
          ),
          SwitchListTile(
            secondary: const Icon(Icons.fingerprint),
            title: const Text('Biometric unlock'),
            subtitle: const Text('Face / fingerprint instead of typing your master password'),
            value: _biometricEnabled,
            onChanged: _onBiometricToggle,
          ),
          const Divider(),
          ListTile(
            leading: const Icon(Icons.lock_outline),
            title: const Text('Lock vault now'),
            subtitle: const Text('Require the master password or biometric again'),
            onTap: _lockNow,
          ),
          if (_message != null)
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
              child: Text(_message!, style: const TextStyle(color: Colors.redAccent)),
            ),
        ],
      ),
    );
  }
}
