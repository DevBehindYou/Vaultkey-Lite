import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../models/credential.dart';
import '../services/vault_channel.dart';
import '../theme.dart';
import 'add_edit_screen.dart';
import 'settings_screen.dart';

class VaultListScreen extends StatefulWidget {
  const VaultListScreen({super.key});

  @override
  State<VaultListScreen> createState() => _VaultListScreenState();
}

class _VaultListScreenState extends State<VaultListScreen> {
  final _channel = VaultChannel.instance;
  List<CredentialSummary> _all = [];
  String _query = '';
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _refresh();
  }

  Future<void> _refresh() async {
    setState(() => _loading = true);
    try {
      final credentials = await _channel.getCredentialSummaries();
      if (!mounted) return;
      setState(() {
        _all = credentials;
        _loading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() => _loading = false);
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text("Couldn't load your logins")),
      );
    }
  }

  /// Copies to the clipboard and, for secrets, clears it after a delay so a
  /// password doesn't linger where another app could read it. Standard
  /// password-manager behaviour.
  Future<void> _copy(String value, String what, {bool sensitive = false}) async {
    await Clipboard.setData(ClipboardData(text: value));
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text('$what copied${sensitive ? ' — clears in 30s' : ''}'),
        duration: const Duration(seconds: 2),
      ),
    );
    if (sensitive) {
      Future.delayed(const Duration(seconds: 30), () {
        // Best-effort overwrite; unconditional so we never leave a secret
        // sitting on the clipboard, matching Bitwarden's default.
        Clipboard.setData(const ClipboardData(text: ''));
      });
    }
  }

  Future<void> _openDetail(CredentialSummary summary) async {
    CredentialDetail? fetched;
    try {
      fetched = await _channel.getCredentialDetail(summary.id);
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text("Couldn't open this login")),
        );
      }
      return;
    }
    if (fetched == null || !mounted) return;
    final detail = fetched; // non-null final -> safe to use inside closures

    var revealPassword = false;
    final action = await showDialog<String>(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, setDialogState) => AlertDialog(
          title: Text(detail.label),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text('Username / email', style: TextStyle(color: VaultKeyColors.muted, fontSize: 12)),
                Row(
                  children: [
                    Expanded(child: SelectableText(detail.username)),
                    IconButton(
                      tooltip: 'Copy username',
                      icon: const Icon(Icons.copy_outlined, size: 20),
                      onPressed: () => _copy(detail.username, 'Username'),
                    ),
                  ],
                ),
                const SizedBox(height: 12),
                const Text('Password', style: TextStyle(color: VaultKeyColors.muted, fontSize: 12)),
                Row(
                  children: [
                    Expanded(
                      child: SelectableText(revealPassword ? detail.password : '••••••••••••'),
                    ),
                    IconButton(
                      tooltip: revealPassword ? 'Hide password' : 'Show password',
                      icon: Icon(revealPassword ? Icons.visibility_off : Icons.visibility, size: 20),
                      onPressed: () => setDialogState(() => revealPassword = !revealPassword),
                    ),
                    IconButton(
                      tooltip: 'Copy password',
                      icon: const Icon(Icons.copy_outlined, size: 20),
                      onPressed: () => _copy(detail.password, 'Password', sensitive: true),
                    ),
                  ],
                ),
                if (detail.notes != null && detail.notes!.isNotEmpty) ...[
                  const SizedBox(height: 12),
                  const Text('Notes', style: TextStyle(color: VaultKeyColors.muted, fontSize: 12)),
                  Text(detail.notes!),
                ],
              ],
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(context).pop('delete'),
              child: Text('Delete', style: TextStyle(color: Theme.of(context).colorScheme.error)),
            ),
            TextButton(onPressed: () => Navigator.of(context).pop('edit'), child: const Text('Edit')),
            TextButton(onPressed: () => Navigator.of(context).pop(), child: const Text('Close')),
          ],
        ),
      ),
    );

    if (!mounted) return;
    if (action == 'edit') {
      await Navigator.of(context).push<bool>(
        MaterialPageRoute(builder: (_) => AddEditScreen(existing: detail)),
      );
      _refresh();
    } else if (action == 'delete') {
      await _confirmDelete(detail);
    }
  }

  Future<void> _confirmDelete(CredentialDetail detail) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Delete this login?'),
        content: Text('“${detail.label}” will be permanently removed from your vault.'),
        actions: [
          TextButton(onPressed: () => Navigator.of(context).pop(false), child: const Text('Cancel')),
          TextButton(
            onPressed: () => Navigator.of(context).pop(true),
            child: Text('Delete', style: TextStyle(color: Theme.of(context).colorScheme.error)),
          ),
        ],
      ),
    );
    if (confirmed != true) return;
    try {
      await _channel.deleteCredential(detail.id);
      if (!mounted) return;
      _refresh();
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Login deleted')));
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text("Couldn't delete — try again")),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final filtered = _all
        .where((c) =>
            c.label.toLowerCase().contains(_query.toLowerCase()) ||
            c.username.toLowerCase().contains(_query.toLowerCase()))
        .toList();

    return Scaffold(
      appBar: AppBar(
        title: const Text('Vault'),
        actions: [
          IconButton(
            tooltip: 'Settings',
            icon: const Icon(Icons.settings_outlined),
            onPressed: () => Navigator.of(context).push(
              MaterialPageRoute(builder: (_) => const SettingsScreen()),
            ),
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton(
        tooltip: 'Add a login',
        onPressed: () async {
          await Navigator.of(context).push(
            MaterialPageRoute(builder: (_) => const AddEditScreen()),
          );
          _refresh();
        },
        child: const Icon(Icons.add),
      ),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.all(16),
            child: TextField(
              decoration: const InputDecoration(
                labelText: 'Search saved logins',
                prefixIcon: Icon(Icons.search),
              ),
              onChanged: (value) => setState(() => _query = value),
            ),
          ),
          if (_loading)
            const Expanded(child: Center(child: CircularProgressIndicator()))
          else if (filtered.isEmpty)
            Expanded(
              child: Center(
                child: Padding(
                  padding: const EdgeInsets.all(24),
                  child: Text(
                    _all.isEmpty ? 'No saved logins yet. Tap + to add your first one.' : 'No matches for "$_query"',
                    style: const TextStyle(color: VaultKeyColors.muted),
                    textAlign: TextAlign.center,
                  ),
                ),
              ),
            )
          else
            Expanded(
              child: ListView.builder(
                itemCount: filtered.length,
                itemBuilder: (context, index) {
                  final c = filtered[index];
                  return ListTile(
                    leading: CircleAvatar(
                      backgroundColor: VaultKeyColors.accentBlue,
                      child: Text(
                        c.label.isNotEmpty ? c.label[0].toUpperCase() : '?',
                        style: const TextStyle(color: Colors.white),
                      ),
                    ),
                    title: Text(c.label),
                    subtitle: Text(c.username),
                    onTap: () => _openDetail(c),
                  );
                },
              ),
            ),
        ],
      ),
    );
  }
}
