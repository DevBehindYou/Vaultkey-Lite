import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'keyboard_channel.dart';

/// Separate Dart entrypoint from the main vault app's main() in lib/main.dart
/// — FlutterVaultIME.kt's DartExecutor.DartEntrypoint runs THIS function, not
/// main(), since the keyboard and the vault app are two different Flutter
/// engines in the same process (see INTEGRATION.md). The annotation is
/// required so the Dart compiler doesn't tree-shake this away as unreachable
/// from main().
@pragma('vm:entry-point')
void keyboardMain() {
  runApp(const KeyboardApp());
}

class KeyboardApp extends StatelessWidget {
  const KeyboardApp({super.key});

  @override
  Widget build(BuildContext context) {
    return const MaterialApp(
      debugShowCheckedModeBanner: false,
      home: Material(
        color: _KeyboardColors.keysBackground,
        child: SafeArea(top: false, child: _KeyboardSurface()),
      ),
    );
  }
}

class _KeyboardColors {
  static const suggestionBarBg = Color(0xFF14151A);
  static const keysBackground = Color(0xFFDCDAD5);
  static const letterKey = Color(0xFFFFFFFF);
  static const letterKeyPressed = Color(0xFFD8DAE0);
  static const specialKey = Color(0xFFD3D6DA);
  static const specialKeyPressed = Color(0xFFC2C5CB);
  static const accentBlue = Color(0xFF2F4EEA);
  static const accentBluePressed = Color(0xFF2439B4);
  static const letterText = Color(0xFF1F1F23);
  static const specialText = Color(0xFF3C3C43);
}

/// Which set of keys is showing. `letters` is QWERTY; `symbols` is the
/// numbers/common-symbols page; `symbols2` is the extra-symbols page.
enum _Layer { letters, symbols, symbols2 }

/// Shift state for the letters layer: momentary [on] (auto-clears after one
/// letter) or sticky [locked] (caps lock, via double-tap).
enum _Shift { off, on, locked }

// Sentinel labels for non-character keys, kept distinct from any real glyph.
const _kShift = 'SHIFT';
const _kDelete = 'DEL';
const _kLayerSymbols = 'SYM';
const _kLayerSymbols2 = 'SYM2';
const _kLayerLetters = 'ABC';
const _kSpace = 'SPACE';
const _kGo = 'GO';

const _sentinels = {_kShift, _kDelete, _kLayerSymbols, _kLayerSymbols2, _kLayerLetters, _kSpace, _kGo};
bool _isSentinel(String key) => _sentinels.contains(key);

class _KeyboardSurface extends StatefulWidget {
  const _KeyboardSurface();

  @override
  State<_KeyboardSurface> createState() => _KeyboardSurfaceState();
}

class _KeyboardSurfaceState extends State<_KeyboardSurface> {
  final _channel = KeyboardChannel.instance;
  _Layer _layer = _Layer.letters;
  _Shift _shift = _Shift.off;
  DateTime? _lastShiftTap;
  List<SuggestionChip> _chips = [];

  @override
  void initState() {
    super.initState();
    _channel.onSuggestionsChanged = (chips) {
      if (mounted) setState(() => _chips = chips);
    };
  }

  void _onKey(String key) {
    switch (key) {
      case _kShift:
        _tapShift();
      case _kDelete:
        HapticFeedback.lightImpact();
        _channel.deleteSurroundingText(1);
      case _kLayerSymbols:
        setState(() => _layer = _Layer.symbols);
      case _kLayerSymbols2:
        setState(() => _layer = _Layer.symbols2);
      case _kLayerLetters:
        setState(() => _layer = _Layer.letters);
      case _kSpace:
        _commitChar(' ');
      case _kGo:
        HapticFeedback.lightImpact();
        _channel.performEditorAction();
      default:
        _commitChar(key);
    }
  }

  void _commitChar(String ch) {
    HapticFeedback.lightImpact();
    final out = (_layer == _Layer.letters && _shift != _Shift.off) ? ch.toUpperCase() : ch;
    _channel.commitText(out);
    // A momentary shift falls back to lowercase after a single character.
    if (_layer == _Layer.letters && _shift == _Shift.on && ch.length == 1) {
      setState(() => _shift = _Shift.off);
    }
  }

  void _tapShift() {
    HapticFeedback.lightImpact();
    final now = DateTime.now();
    final isDoubleTap = _lastShiftTap != null && now.difference(_lastShiftTap!) < const Duration(milliseconds: 300);
    _lastShiftTap = now;
    setState(() {
      _shift = isDoubleTap
          ? _Shift.locked
          : (_shift == _Shift.off ? _Shift.on : _Shift.off);
    });
  }

  bool get _upper => _layer == _Layer.letters && _shift != _Shift.off;

  @override
  Widget build(BuildContext context) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        _suggestionBar(),
        Padding(
          padding: const EdgeInsets.fromLTRB(4, 6, 4, 6),
          child: Column(
            children: [
              for (final row in _rowsForLayer()) ...[
                _keyRow(row),
                const SizedBox(height: 6),
              ],
              _bottomRow(),
            ],
          ),
        ),
      ],
    );
  }

  /// The three character rows (everything above the bottom function row) for
  /// the current layer. Each entry is either a single character or a sentinel.
  List<List<String>> _rowsForLayer() {
    switch (_layer) {
      case _Layer.letters:
        return [
          ['q', 'w', 'e', 'r', 't', 'y', 'u', 'i', 'o', 'p'],
          ['a', 's', 'd', 'f', 'g', 'h', 'j', 'k', 'l'],
          [_kShift, 'z', 'x', 'c', 'v', 'b', 'n', 'm', _kDelete],
        ];
      case _Layer.symbols:
        return [
          ['1', '2', '3', '4', '5', '6', '7', '8', '9', '0'],
          ['@', '#', r'$', '_', '&', '-', '+', '(', ')', '/'],
          [_kLayerSymbols2, '*', '"', "'", ':', ';', '!', '?', _kDelete],
        ];
      case _Layer.symbols2:
        return [
          ['~', '`', '|', '•', '√', 'π', '÷', '×', '¶', 'Δ'],
          ['£', '¢', '€', '¥', '^', '°', '=', '{', '}', r'\'],
          [_kLayerSymbols, '%', '©', '®', '™', '[', ']', '<', _kDelete],
        ];
    }
  }

  Widget _keyRow(List<String> keys) {
    // The middle letter row is inset slightly, like a physical QWERTY stagger.
    final inset = _layer == _Layer.letters && keys.length == 9 && keys.first == 'a';
    final row = Row(
      children: keys.map((key) {
        final special = _isSentinel(key);
        final wide = key == _kShift || key == _kDelete || key == _kLayerSymbols || key == _kLayerSymbols2;
        return Expanded(
          flex: wide ? 15 : 10,
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 3),
            child: _KeyButton(
              label: _displayLabel(key),
              isSpecial: special,
              icon: _iconFor(key),
              shiftActive: key == _kShift ? _shift : null,
              repeatWhilePressed: key == _kDelete,
              onTap: () => _onKey(key),
            ),
          ),
        );
      }).toList(),
    );
    if (!inset) return row;
    return Padding(padding: const EdgeInsets.symmetric(horizontal: 18), child: row);
  }

  Widget _bottomRow() {
    final layerToggle = _layer == _Layer.letters ? _kLayerSymbols : _kLayerLetters;
    final layerLabel = _layer == _Layer.letters ? '?123' : 'ABC';
    return Row(
      children: [
        _flexKey(14, _KeyButton(label: layerLabel, isSpecial: true, small: true, onTap: () => _onKey(layerToggle))),
        const SizedBox(width: 6),
        _flexKey(10, _KeyButton(label: ',', onTap: () => _onKey(','))),
        const SizedBox(width: 6),
        _flexKey(40, _KeyButton(label: 'space', isSpecial: true, small: true, onTap: () => _onKey(_kSpace))),
        const SizedBox(width: 6),
        _flexKey(10, _KeyButton(label: '.', onTap: () => _onKey('.'))),
        const SizedBox(width: 6),
        _flexKey(16, _KeyButton(label: 'go', isSpecial: true, small: true, onTap: () => _onKey(_kGo))),
      ],
    );
  }

  Widget _flexKey(int flex, Widget child) => Expanded(flex: flex, child: child);

  String _displayLabel(String key) {
    if (_isSentinel(key)) {
      switch (key) {
        case _kLayerSymbols2:
          return '=\\<';
        case _kLayerSymbols:
          return '?123';
        default:
          return ''; // shift/delete render as icons
      }
    }
    return _upper ? key.toUpperCase() : key;
  }

  IconData? _iconFor(String key) {
    switch (key) {
      case _kShift:
        return _shift == _Shift.locked ? Icons.keyboard_capslock : Icons.arrow_upward;
      case _kDelete:
        return Icons.backspace_outlined;
      default:
        return null;
    }
  }

  Widget _suggestionBar() {
    return Container(
      width: double.infinity,
      color: _KeyboardColors.suggestionBarBg,
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 8),
      child: _chips.isEmpty
          ? const SizedBox(height: 40)
          : SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              child: Row(
                children: _chips
                    .map((chip) => Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 4),
                          child: _SuggestionPill(
                            label: 'Use ${chip.label} login',
                            onTap: () {
                              HapticFeedback.lightImpact();
                              _channel.insertCredential(chip.id);
                            },
                          ),
                        ))
                    .toList(),
              ),
            ),
    );
  }
}

class _KeyButton extends StatefulWidget {
  final String label;
  final bool isSpecial;
  final bool small;
  final IconData? icon;

  /// When set, this is the shift key and the value drives its highlight.
  final _Shift? shiftActive;

  /// If true, holding the key fires [onTap] repeatedly (used for backspace).
  final bool repeatWhilePressed;
  final VoidCallback onTap;

  const _KeyButton({
    required this.label,
    required this.onTap,
    this.isSpecial = false,
    this.small = false,
    this.icon,
    this.shiftActive,
    this.repeatWhilePressed = false,
  });

  @override
  State<_KeyButton> createState() => _KeyButtonState();
}

class _KeyButtonState extends State<_KeyButton> {
  bool _pressed = false;
  Timer? _initialDelay;
  Timer? _repeatTimer;

  void _startRepeat() {
    if (!widget.repeatWhilePressed) return;
    _initialDelay = Timer(const Duration(milliseconds: 400), () {
      _repeatTimer = Timer.periodic(const Duration(milliseconds: 55), (_) => widget.onTap());
    });
  }

  void _stopRepeat() {
    _initialDelay?.cancel();
    _repeatTimer?.cancel();
    _initialDelay = null;
    _repeatTimer = null;
  }

  @override
  void dispose() {
    _stopRepeat();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final locked = widget.shiftActive == _Shift.locked;
    final shiftOn = widget.shiftActive == _Shift.on || locked;

    final Color normalColor;
    final Color pressedColor;
    final Color textColor;
    if (shiftOn) {
      normalColor = _KeyboardColors.accentBlue;
      pressedColor = _KeyboardColors.accentBluePressed;
      textColor = Colors.white;
    } else if (widget.isSpecial) {
      normalColor = _KeyboardColors.specialKey;
      pressedColor = _KeyboardColors.specialKeyPressed;
      textColor = _KeyboardColors.specialText;
    } else {
      normalColor = _KeyboardColors.letterKey;
      pressedColor = _KeyboardColors.letterKeyPressed;
      textColor = _KeyboardColors.letterText;
    }

    return GestureDetector(
      onTapDown: (_) {
        setState(() => _pressed = true);
        _startRepeat();
      },
      onTapUp: (_) {
        setState(() => _pressed = false);
        _stopRepeat();
      },
      onTapCancel: () {
        setState(() => _pressed = false);
        _stopRepeat();
      },
      onTap: widget.onTap,
      child: Container(
        height: 48,
        decoration: BoxDecoration(
          color: _pressed ? pressedColor : normalColor,
          borderRadius: BorderRadius.circular(6),
        ),
        alignment: Alignment.center,
        child: widget.icon != null
            ? Icon(widget.icon, size: 18, color: textColor)
            : Text(
                widget.label,
                style: TextStyle(
                  color: textColor,
                  fontSize: widget.small ? 12 : 16,
                  fontWeight: FontWeight.w400,
                ),
              ),
      ),
    );
  }
}

class _SuggestionPill extends StatelessWidget {
  final String label;
  final VoidCallback onTap;
  const _SuggestionPill({required this.label, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        height: 40,
        padding: const EdgeInsets.symmetric(horizontal: 14),
        decoration: BoxDecoration(
          color: _KeyboardColors.accentBlue,
          borderRadius: BorderRadius.circular(20),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.circle, size: 6, color: Colors.white),
            const SizedBox(width: 8),
            Text(
              label,
              style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w700, fontSize: 14),
            ),
          ],
        ),
      ),
    );
  }
}
