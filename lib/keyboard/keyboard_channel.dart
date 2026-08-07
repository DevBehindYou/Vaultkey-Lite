import 'package:flutter/services.dart';

/// One suggestion chip, pushed from Kotlin's CredentialSuggestionInjector —
/// see FlutterVaultIME.kt's pushSuggestions().
class SuggestionChip {
  final String id;
  final String label;
  SuggestionChip({required this.id, required this.label});

  factory SuggestionChip.fromMap(Map<Object?, Object?> map) =>
      SuggestionChip(id: map['id'] as String, label: map['label'] as String);
}

/// The keyboard's side of the "com.vaultkey.app/keyboard" channel —
/// deliberately separate from lib/services/vault_channel.dart, since this
/// runs in a different Flutter engine (hosted inside FlutterVaultIME, a
/// Service) than the main vault app (hosted inside MainActivity, an
/// Activity) — see INTEGRATION.md.
class KeyboardChannel {
  KeyboardChannel._() {
    _channel.setMethodCallHandler(_handleNativeCall);
  }
  static final KeyboardChannel instance = KeyboardChannel._();

  final MethodChannel _channel = const MethodChannel('com.vaultkey.app/keyboard');

  /// Called whenever Kotlin pushes a new suggestion list (or an empty one,
  /// to clear it) — set this from the widget that renders the strip.
  void Function(List<SuggestionChip> chips)? onSuggestionsChanged;

  Future<dynamic> _handleNativeCall(MethodCall call) async {
    if (call.method == 'onSuggestions') {
      final raw = (call.arguments as List<Object?>? ?? []);
      final chips = raw.map((e) => SuggestionChip.fromMap(Map<Object?, Object?>.from(e as Map))).toList();
      onSuggestionsChanged?.call(chips);
    }
  }

  Future<void> commitText(String text) => _channel.invokeMethod('commitText', {'text': text});

  Future<void> deleteSurroundingText([int count = 1]) =>
      _channel.invokeMethod('deleteSurroundingText', {'count': count});

  Future<void> performEditorAction() => _channel.invokeMethod('performEditorAction');

  Future<void> insertCredential(String chipId) =>
      _channel.invokeMethod('insertCredential', {'chipId': chipId});
}
