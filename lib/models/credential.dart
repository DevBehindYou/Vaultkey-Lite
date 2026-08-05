/// One row in the vault list — deliberately does NOT carry the password,
/// matching the split between CredentialRepository.getAll() (used for lists)
/// and .getById() (used once a specific credential is opened) — see
/// DATA_FLOW.md.
class CredentialSummary {
  final String id;
  final String label;
  final String username;

  CredentialSummary({required this.id, required this.label, required this.username});

  factory CredentialSummary.fromMap(Map<Object?, Object?> map) => CredentialSummary(
        id: map['id'] as String,
        label: map['label'] as String,
        username: map['username'] as String,
      );
}

/// Full detail, including the plaintext password — only ever requested when
/// a specific credential is opened, and only ever held in memory as long as
/// that screen is on-screen (no local caching beyond the widget's state).
class CredentialDetail {
  final String id;
  final String label;
  final String username;
  final String password;
  final String? notes;

  CredentialDetail({
    required this.id,
    required this.label,
    required this.username,
    required this.password,
    required this.notes,
  });

  factory CredentialDetail.fromMap(Map<Object?, Object?> map) => CredentialDetail(
        id: map['id'] as String,
        label: map['label'] as String,
        username: map['username'] as String,
        password: map['password'] as String,
        notes: map['notes'] as String?,
      );
}
