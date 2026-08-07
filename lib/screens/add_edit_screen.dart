import 'package:flutter/material.dart';
import '../models/credential.dart';
import '../services/vault_channel.dart';
import '../theme.dart';
import '../utils/password.dart';

/// Add a new login, or edit an existing one when [existing] is supplied.
///
/// Website domain and app package are two explicit, both-optional fields —
/// same reasoning as the pre-Flutter Compose version: one credential can carry
/// both matches at once, and guessing the type from a single field was
/// rejected during the original build.
class AddEditScreen extends StatefulWidget {
  final CredentialDetail? existing;
  const AddEditScreen({super.key, this.existing});

  @override
  State<AddEditScreen> createState() => _AddEditScreenState();
}

class _AddEditScreenState extends State<AddEditScreen> {
  final _channel = VaultChannel.instance;
  late final TextEditingController _label;
  late final TextEditingController _webDomain;
  late final TextEditingController _packageName;
  late final TextEditingController _username;
  late final TextEditingController _password;
  late final TextEditingController _notes;
  String? _error;
  bool _saving = false;
  bool _obscure = true;

  bool get _isEditing => widget.existing != null;

  @override
  void initState() {
    super.initState();
    final e = widget.existing;
    _label = TextEditingController(text: e?.label ?? '');
    _webDomain = TextEditingController(text: e?.webDomain ?? '');
    _packageName = TextEditingController(text: e?.packageName ?? '');
    _username = TextEditingController(text: e?.username ?? '');
    _password = TextEditingController(text: e?.password ?? '');
    _notes = TextEditingController(text: e?.notes ?? '');
  }

  @override
  void dispose() {
    _label.dispose();
    _webDomain.dispose();
    _packageName.dispose();
    _username.dispose();
    _password.dispose();
    _notes.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    if (_label.text.trim().isEmpty || (_webDomain.text.trim().isEmpty && _packageName.text.trim().isEmpty)) {
      setState(() => _error = 'Add a label and at least one of website domain / app package');
      return;
    }
    setState(() {
      _error = null;
      _saving = true;
    });
    try {
      final notes = _notes.text.trim().isEmpty ? null : _notes.text.trim();
      if (_isEditing) {
        await _channel.updateCredential(
          id: widget.existing!.id,
          label: _label.text.trim(),
          webDomain: _webDomain.text.trim(),
          packageName: _packageName.text.trim(),
          username: _username.text,
          password: _password.text,
          notes: notes,
        );
      } else {
        await _channel.addCredential(
          label: _label.text.trim(),
          webDomain: _webDomain.text.trim(),
          packageName: _packageName.text.trim(),
          username: _username.text,
          password: _password.text,
          notes: notes,
        );
      }
      if (mounted) Navigator.of(context).pop(true);
    } catch (e) {
      if (mounted) {
        setState(() {
          _error = "Couldn't save — try again";
          _saving = false;
        });
      }
    }
  }

  void _generate() {
    setState(() {
      _password.text = PasswordGenerator.generate();
      _obscure = false; // reveal so the user can see what was generated
    });
  }

  @override
  Widget build(BuildContext context) {
    final strength = PasswordStrength.of(_password.text);
    return Scaffold(
      appBar: AppBar(title: Text(_isEditing ? 'Edit login' : 'Add login')),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            TextField(
              controller: _label,
              textInputAction: TextInputAction.next,
              decoration: const InputDecoration(labelText: 'Label (e.g. GitHub)'),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _webDomain,
              textInputAction: TextInputAction.next,
              keyboardType: TextInputType.url,
              decoration: const InputDecoration(labelText: 'Website domain (optional)'),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _packageName,
              textInputAction: TextInputAction.next,
              decoration: const InputDecoration(labelText: 'App package name (optional)'),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _username,
              textInputAction: TextInputAction.next,
              decoration: const InputDecoration(labelText: 'Username / email'),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _password,
              obscureText: _obscure,
              onChanged: (_) => setState(() {}),
              decoration: InputDecoration(
                labelText: 'Password',
                suffixIcon: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    IconButton(
                      tooltip: _obscure ? 'Show password' : 'Hide password',
                      icon: Icon(_obscure ? Icons.visibility : Icons.visibility_off),
                      onPressed: () => setState(() => _obscure = !_obscure),
                    ),
                    IconButton(
                      tooltip: 'Generate strong password',
                      icon: const Icon(Icons.autorenew),
                      onPressed: _generate,
                    ),
                  ],
                ),
              ),
            ),
            if (_password.text.isNotEmpty) ...[
              const SizedBox(height: 8),
              _StrengthBar(strength: strength),
            ],
            const SizedBox(height: 12),
            TextField(
              controller: _notes,
              maxLines: 3,
              minLines: 1,
              decoration: const InputDecoration(labelText: 'Notes (optional)'),
            ),
            if (_error != null) ...[
              const SizedBox(height: 8),
              Text(_error!, style: TextStyle(color: Theme.of(context).colorScheme.error)),
            ],
            const SizedBox(height: 20),
            ElevatedButton(
              onPressed: _saving ? null : _save,
              child: Text(_isEditing ? 'Save changes' : 'Save to Vault'),
            ),
          ],
        ),
      ),
    );
  }
}

class _StrengthBar extends StatelessWidget {
  final PasswordStrength strength;
  const _StrengthBar({required this.strength});

  @override
  Widget build(BuildContext context) {
    const colors = [
      Color(0xFFD32F2F), // very weak
      Color(0xFFF57C00), // weak
      Color(0xFFFBC02D), // fair
      Color(0xFF7CB342), // good
      Color(0xFF388E3C), // strong
    ];
    final color = colors[strength.score];
    return Row(
      children: [
        Expanded(
          child: ClipRRect(
            borderRadius: BorderRadius.circular(4),
            child: LinearProgressIndicator(
              value: (strength.score + 1) / 5,
              minHeight: 6,
              backgroundColor: VaultKeyColors.line,
              color: color,
            ),
          ),
        ),
        const SizedBox(width: 8),
        Text(strength.label, style: TextStyle(color: color, fontSize: 12, fontWeight: FontWeight.w600)),
      ],
    );
  }
}
