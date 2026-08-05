import 'package:flutter/material.dart';
import '../services/vault_channel.dart';

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
    final enabled = await _channel.isBiometricEnabled();
    final available = await _channel.isBiometricAvailable();
    if (!mounted) return;
    setState(() {
      _biometricEnabled = enabled;
      _biometricAvailable = available;
    });
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
      _message = enrolled ? null : _message;
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Settings')),
      body: ListView(
        children: [
          ListTile(
            title: const Text('VaultKey Keyboard'),
            subtitle: const Text('Set as your default input method'),
            onTap: _channel.openImeSettings,
          ),
          ListTile(
            title: const Text('Autofill service'),
            subtitle: const Text('Needed for browser/website matching'),
            onTap: _channel.openAutofillSettings,
          ),
          SwitchListTile(
            title: const Text('Biometric unlock'),
            subtitle: const Text('Face / fingerprint instead of typing your master password'),
            value: _biometricEnabled,
            onChanged: _onBiometricToggle,
          ),
          if (_message != null)
            ListTile(title: Text(_message!)),
        ],
      ),
    );
  }
}
