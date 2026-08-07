import 'package:flutter_test/flutter_test.dart';
import 'package:vaultkey/utils/password.dart';

void main() {
  group('PasswordGenerator', () {
    test('generates the requested length', () {
      expect(PasswordGenerator.generate(length: 24).length, 24);
    });

    test('includes lower, upper and digits by default', () {
      final pw = PasswordGenerator.generate(length: 48);
      expect(RegExp(r'[a-z]').hasMatch(pw), isTrue);
      expect(RegExp(r'[A-Z]').hasMatch(pw), isTrue);
      expect(RegExp(r'[0-9]').hasMatch(pw), isTrue);
    });

    test('omits ambiguous characters (l, I, O, 0, 1)', () {
      final pw = PasswordGenerator.generate(length: 300);
      for (final c in ['l', 'I', 'O', '0', '1']) {
        expect(pw.contains(c), isFalse, reason: 'should not contain "$c"');
      }
    });

    test('excludes symbols when asked', () {
      final pw = PasswordGenerator.generate(length: 80, includeSymbols: false);
      expect(RegExp(r'[^A-Za-z0-9]').hasMatch(pw), isFalse);
    });

    test('two generations differ (uses a real CSPRNG)', () {
      expect(PasswordGenerator.generate(), isNot(equals(PasswordGenerator.generate())));
    });
  });

  group('PasswordStrength', () {
    test('empty string is score 0 with no label', () {
      final s = PasswordStrength.of('');
      expect(s.score, 0);
      expect(s.label, '');
    });

    test('a long, mixed-class password scores Strong', () {
      final s = PasswordStrength.of('Tr0ub4dour&3xtra-Long!!');
      expect(s.score, 4);
      expect(s.label, 'Strong');
    });

    test('a short simple password scores low', () {
      expect(PasswordStrength.of('abc').score, lessThanOrEqualTo(1));
    });
  });
}
