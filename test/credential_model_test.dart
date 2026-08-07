import 'package:flutter_test/flutter_test.dart';
import 'package:vaultkey/models/credential.dart';

void main() {
  test('CredentialSummary.fromMap reads id/label/username', () {
    final s = CredentialSummary.fromMap({'id': '1', 'label': 'GitHub', 'username': 'a@b.com'});
    expect(s.id, '1');
    expect(s.label, 'GitHub');
    expect(s.username, 'a@b.com');
  });

  test('CredentialDetail.fromMap carries password, nullable notes, and matches', () {
    final d = CredentialDetail.fromMap({
      'id': '1',
      'label': 'GitHub',
      'username': 'a@b.com',
      'password': 'pw',
      'notes': null,
      'webDomain': 'github.com',
      'packageName': null,
    });
    expect(d.password, 'pw');
    expect(d.notes, isNull);
    expect(d.webDomain, 'github.com');
    expect(d.packageName, isNull);
  });

  test('CredentialDetail.fromMap tolerates absent match keys (autofill path)', () {
    final d = CredentialDetail.fromMap({
      'id': '1',
      'label': 'GitHub',
      'username': 'a@b.com',
      'password': 'pw',
      'notes': 'hi',
    });
    expect(d.webDomain, isNull);
    expect(d.packageName, isNull);
    expect(d.notes, 'hi');
  });
}
