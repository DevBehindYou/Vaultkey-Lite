import 'package:flutter/material.dart';
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
    final credentials = await _channel.getCredentialSummaries();
    if (!mounted) return;
    setState(() {
      _all = credentials;
      _loading = false;
    });
  }

  Future<void> _openDetail(CredentialSummary summary) async {
    final detail = await _channel.getCredentialDetail(summary.id);
    if (detail == null || !mounted) return;
    var revealPassword = false;
    await showDialog<void>(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, setDialogState) => AlertDialog(
          title: Text(detail.label),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text('Username / email', style: TextStyle(color: VaultKeyColors.muted, fontSize: 12)),
              SelectableText(detail.username),
              const SizedBox(height: 12),
              const Text('Password', style: TextStyle(color: VaultKeyColors.muted, fontSize: 12)),
              Row(
                children: [
                  Expanded(child: SelectableText(revealPassword ? detail.password : '••••••••••••')),
                  IconButton(
                    icon: Icon(revealPassword ? Icons.visibility_off : Icons.visibility),
                    onPressed: () => setDialogState(() => revealPassword = !revealPassword),
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
          actions: [
            TextButton(onPressed: () => Navigator.of(context).pop(), child: const Text('Close')),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final filtered = _all.where((c) => c.label.toLowerCase().contains(_query.toLowerCase())).toList();

    return Scaffold(
      appBar: AppBar(
        title: const Text('Vault'),
        actions: [
          IconButton(
            icon: const Icon(Icons.settings_outlined),
            onPressed: () => Navigator.of(context).push(
              MaterialPageRoute(builder: (_) => const SettingsScreen()),
            ),
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton(
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
