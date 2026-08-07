import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:vaultkey/services/vault_channel.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const channel = MethodChannel('com.vaultkey.app/vault');
  final messenger = TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;

  tearDown(() => messenger.setMockMethodCallHandler(channel, null));

  test('getVaultState maps "unlocked" to VaultState.unlocked', () async {
    messenger.setMockMethodCallHandler(channel, (call) async {
      expect(call.method, 'getVaultState');
      return 'unlocked';
    });
    expect(await VaultChannel.instance.getVaultState(), VaultState.unlocked);
  });

  test('getVaultState maps "uninitialized" correctly', () async {
    messenger.setMockMethodCallHandler(channel, (call) async => 'uninitialized');
    expect(await VaultChannel.instance.getVaultState(), VaultState.uninitialized);
  });

  test('getVaultState defaults unknown values to locked (fail-safe)', () async {
    messenger.setMockMethodCallHandler(channel, (call) async => 'something-unexpected');
    expect(await VaultChannel.instance.getVaultState(), VaultState.locked);
  });

  test('createVault forwards the password and returns the native bool', () async {
    String? seenPassword;
    messenger.setMockMethodCallHandler(channel, (call) async {
      expect(call.method, 'createVault');
      seenPassword = (call.arguments as Map)['password'] as String;
      return true;
    });
    final ok = await VaultChannel.instance.createVault('hunter2long');
    expect(ok, isTrue);
    expect(seenPassword, 'hunter2long');
  });
}
