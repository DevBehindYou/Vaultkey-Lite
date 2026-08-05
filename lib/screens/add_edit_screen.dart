import 'package:flutter/material.dart';
import '../services/vault_channel.dart';

/// Matches SCR.04. Website domain and app package are two explicit,
/// both-optional fields — same reasoning as the pre-Flutter Compose version
/// (legacy_compose_ui/.../AddEditCredentialScreen.kt's doc comment): one
/// credential can carry both matches at once, and guessing the type from a
/// single field was rejected during the original build.
class AddEditScreen extends StatefulWidget {
  const AddEditScreen({super.key});

  @override
  State<AddEditScreen> createState() => _AddEditScreenState();
}

class _AddEditScreenState extends State<AddEditScreen> {
  final _channel = VaultChannel.instance;
  final _label = TextEditingController();
  final _webDomain = TextEditingController();
  final _packageName = TextEditingController();
  final _username = TextEditingController();
  final _password = TextEditingController();
  final _notes = TextEditingController();
  String? _error;
  bool _saving = false;

  Future<void> _save() async {
    if (_label.text.trim().isEmpty || (_webDomain.text.trim().isEmpty && _packageName.text.trim().isEmpty)) {
      setState(() => _error = 'Add a label and at least one of website domain / app package');
      return;
    }
    setState(() {
      _error = null;
      _saving = true;
    });
    await _channel.addCredential(
      label: _label.text.trim(),
      webDomain: _webDomain.text.trim(),
      packageName: _packageName.text.trim(),
      username: _username.text,
      password: _password.text,
      notes: _notes.text.trim().isEmpty ? null : _notes.text.trim(),
    );
    if (mounted) Navigator.of(context).pop();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Add login')),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            TextField(controller: _label, decoration: const InputDecoration(labelText: 'Label (e.g. GitHub)')),
            const SizedBox(height: 12),
            TextField(controller: _webDomain, decoration: const InputDecoration(labelText: 'Website domain (optional)')),
            const SizedBox(height: 12),
            TextField(controller: _packageName, decoration: const InputDecoration(labelText: 'App package name (optional)')),
            const SizedBox(height: 12),
            TextField(controller: _username, decoration: const InputDecoration(labelText: 'Username / email')),
            const SizedBox(height: 12),
            TextField(controller: _password, obscureText: true, decoration: const InputDecoration(labelText: 'Password')),
            const SizedBox(height: 12),
            TextField(controller: _notes, decoration: const InputDecoration(labelText: 'Notes (optional)')),
            if (_error != null) ...[
              const SizedBox(height: 8),
              Text(_error!, style: TextStyle(color: Theme.of(context).colorScheme.error)),
            ],
            const SizedBox(height: 20),
            ElevatedButton(
              onPressed: _saving ? null : _save,
              child: const Text('Save to Vault'),
            ),
          ],
        ),
      ),
    );
  }
}
