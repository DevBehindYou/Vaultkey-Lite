import 'dart:math';

/// Cryptographically-secure password generator. Uses [Random.secure] (backed by
/// the platform CSPRNG) — never the default [Random], which is predictable.
class PasswordGenerator {
  // Ambiguous glyphs (l/1/I, O/0) are intentionally excluded so a generated
  // password can still be read off-screen and typed by hand if ever needed.
  static const _lower = 'abcdefghijkmnpqrstuvwxyz';
  static const _upper = 'ABCDEFGHJKLMNPQRSTUVWXYZ';
  static const _digits = '23456789';
  static const _symbols = r'!@#$%^&*()-_=+[]{}';

  static String generate({int length = 20, bool includeSymbols = true}) {
    final rng = Random.secure();
    final pools = <String>[_lower, _upper, _digits, if (includeSymbols) _symbols];
    final all = pools.join();

    // Guarantee at least one character from each enabled class, then fill the
    // rest from the full alphabet, then shuffle so the guaranteed characters
    // aren't stuck at the front.
    final chars = <String>[
      for (final pool in pools) pool[rng.nextInt(pool.length)],
    ];
    while (chars.length < length) {
      chars.add(all[rng.nextInt(all.length)]);
    }
    for (var i = chars.length - 1; i > 0; i--) {
      final j = rng.nextInt(i + 1);
      final tmp = chars[i];
      chars[i] = chars[j];
      chars[j] = tmp;
    }
    return chars.join();
  }
}

/// A lightweight 0..4 strength score with a label. Deliberately a small
/// heuristic (length tiers + character-class variety) rather than pulling in a
/// full zxcvbn port — proportionate for an offline locker, and it never leaves
/// the device.
class PasswordStrength {
  final int score; // 0 (empty/very weak) .. 4 (strong)
  final String label;
  const PasswordStrength(this.score, this.label);

  static const _labels = ['Very weak', 'Weak', 'Fair', 'Good', 'Strong'];

  static PasswordStrength of(String pw) {
    if (pw.isEmpty) return const PasswordStrength(0, '');
    var classes = 0;
    if (RegExp(r'[a-z]').hasMatch(pw)) classes++;
    if (RegExp(r'[A-Z]').hasMatch(pw)) classes++;
    if (RegExp(r'[0-9]').hasMatch(pw)) classes++;
    if (RegExp(r'[^A-Za-z0-9]').hasMatch(pw)) classes++;

    var score = 0;
    if (pw.length >= 8) score++;
    if (pw.length >= 12) score++;
    if (pw.length >= 16) score++;
    if (classes >= 3) score++;
    score = score.clamp(0, 4);
    return PasswordStrength(score, _labels[score]);
  }
}
