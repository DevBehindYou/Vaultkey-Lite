import 'package:flutter/services.dart';
import '../models/credential.dart';

enum VaultState { uninitialized, locked, unlocked }

/// The single point of contact with the native side (MainActivity.kt in
/// android/app). Every method here maps 1:1 to a `when (call.method)` branch
/// there — see DATA_FLOW.md for the full sequence diagrams and
/// INTEGRATION.md for why the native side can answer these instantly
/// (VaultKeyGraph is a same-process singleton, not an IPC round-trip to a
/// separate service).
class VaultChannel {
  VaultChannel._();
  static final VaultChannel instance = VaultChannel._();

  final MethodChannel _channel = const MethodChannel('com.vaultkey.app/vault');

  Future<VaultState> getVaultState() async {
    final name = await _channel.invokeMethod<String>('getVaultState');
    switch (name) {
      case 'uninitialized':
        return VaultState.uninitialized;
      case 'unlocked':
        return VaultState.unlocked;
      default:
        return VaultState.locked;
    }
  }

  Future<bool> createVault(String password) async =>
      (await _channel.invokeMethod<bool>('createVault', {'password': password})) ?? false;

  Future<bool> unlockWithPassword(String password) async =>
      (await _channel.invokeMethod<bool>('unlockWithPassword', {'password': password})) ?? false;

  Future<void> lock() => _channel.invokeMethod('lock');

  Future<bool> isBiometricAvailable() async =>
      (await _channel.invokeMethod<bool>('isBiometricAvailable')) ?? false;

  Future<bool> isBiometricEnabled() async =>
      (await _channel.invokeMethod<bool>('isBiometricEnabled')) ?? false;

  /// Shows the system BiometricPrompt (enrollment). Returns false if the
  /// user cancels, authentication fails, or the device has no biometrics
  /// set up — never throws for those ordinary cases.
  Future<bool> enrollBiometric() async =>
      (await _channel.invokeMethod<bool>('enrollBiometric')) ?? false;

  Future<bool> unlockWithBiometric() async =>
      (await _channel.invokeMethod<bool>('unlockWithBiometric')) ?? false;

  Future<void> disableBiometric() => _channel.invokeMethod('disableBiometric');

  Future<List<CredentialSummary>> getCredentialSummaries() async {
    final result = await _channel.invokeMethod<List<Object?>>('getCredentialSummaries');
    return (result ?? [])
        .map((e) => CredentialSummary.fromMap(Map<Object?, Object?>.from(e as Map)))
        .toList();
  }

  Future<CredentialDetail?> getCredentialDetail(String id) async {
    final result = await _channel.invokeMethod<Map<Object?, Object?>>('getCredentialDetail', {'id': id});
    if (result == null) return null;
    return CredentialDetail.fromMap(result);
  }

  Future<void> addCredential({
    required String label,
    required String webDomain,
    required String packageName,
    required String username,
    required String password,
    String? notes,
  }) =>
      _channel.invokeMethod('addCredential', {
        'label': label,
        'webDomain': webDomain,
        'packageName': packageName,
        'username': username,
        'password': password,
        'notes': notes,
      });

  Future<void> openImeSettings() => _channel.invokeMethod('openImeSettings');

  Future<void> openAutofillSettings() => _channel.invokeMethod('openAutofillSettings');
}
