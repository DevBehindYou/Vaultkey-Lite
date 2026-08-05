import 'package:flutter/material.dart';
import '../services/vault_channel.dart';
import '../theme.dart';
import 'vault_list_screen.dart';

class UnlockScreen extends StatefulWidget {
  const UnlockScreen({super.key});

  @override
  State<UnlockScreen> createState() => _UnlockScreenState();
}

class _UnlockScreenState extends State<UnlockScreen> {
  final _channel = VaultChannel.instance;
  final _passwordController = TextEditingController();
  final _confirmController = TextEditingController();

  bool _isFirstRun = true;
  bool _checkingState = true;
  bool _biometricAvailable = false;
  String? _error;
  bool _busy = false;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final state = await _channel.getVaultState();
    final biometricAvailable = await _channel.isBiometricAvailable();
    if (!mounted) return;
    setState(() {
      _isFirstRun = state == VaultState.uninitialized;
      _biometricAvailable = biometricAvailable && state == VaultState.locked;
      _checkingState = false;
    });
  }

  Future<void> _goToVault() async {
    if (!mounted) return;
    Navigator.of(context).pushReplacement(
      MaterialPageRoute(builder: (_) => const VaultListScreen()),
    );
  }

  Future<void> _submit() async {
    setState(() {
      _error = null;
      _busy = true;
    });

    if (_isFirstRun) {
      if (_passwordController.text.length < 8) {
        setState(() {
          _error = 'Use at least 8 characters';
          _busy = false;
        });
        return;
      }
      if (_passwordController.text != _confirmController.text) {
        setState(() {
          _error = "Passwords don't match";
          _busy = false;
        });
        return;
      }
      await _channel.createVault(_passwordController.text);
      if (await _channel.isBiometricAvailable()) {
        // Offer enrollment right away — declining just proceeds to the vault.
        await _channel.enrollBiometric();
      }
      await _goToVault();
    } else {
      final ok = await _channel.unlockWithPassword(_passwordController.text);
      if (ok) {
        await _goToVault();
      } else {
        setState(() {
          _error = 'Wrong password';
          _busy = false;
        });
      }
    }
  }

  Future<void> _tryBiometric() async {
    final ok = await _channel.unlockWithBiometric();
    if (ok) {
      await _goToVault();
    } else if (mounted) {
      setState(() => _error = 'Biometric unlock failed — use your master password');
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_checkingState) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }

    return Scaffold(
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(32),
          child: Center(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  _isFirstRun ? 'Create your master password' : 'Unlock VaultKey',
                  style: Theme.of(context).textTheme.headlineSmall,
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 8),
                Text(
                  _isFirstRun
                      ? "This unlocks your vault. There's no reset — if it's lost, the vault can't be recovered, on purpose."
                      : 'Use your master password or biometric to continue',
                  textAlign: TextAlign.center,
                  style: const TextStyle(color: VaultKeyColors.muted),
                ),
                const SizedBox(height: 24),
                TextField(
                  controller: _passwordController,
                  obscureText: true,
                  decoration: const InputDecoration(labelText: 'Master password'),
                ),
                if (_isFirstRun) ...[
                  const SizedBox(height: 12),
                  TextField(
                    controller: _confirmController,
                    obscureText: true,
                    decoration: const InputDecoration(labelText: 'Confirm password'),
                  ),
                ],
                if (_error != null) ...[
                  const SizedBox(height: 8),
                  Text(_error!, style: TextStyle(color: Theme.of(context).colorScheme.error)),
                ],
                const SizedBox(height: 20),
                ElevatedButton(
                  onPressed: _busy ? null : _submit,
                  child: Text(_isFirstRun ? 'Create vault' : 'Unlock'),
                ),
                if (_biometricAvailable) ...[
                  const SizedBox(height: 8),
                  TextButton(
                    onPressed: _tryBiometric,
                    child: const Text('Use Face / Fingerprint instead'),
                  ),
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }
}
