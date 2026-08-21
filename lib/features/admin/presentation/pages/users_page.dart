import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/models/app_role.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/widgets/glass_card.dart';
import '../../../catalog/providers/role_provider.dart';
import '../../providers/admin_providers.dart';

class UsersPage extends ConsumerStatefulWidget {
  const UsersPage({super.key});
  @override
  ConsumerState<UsersPage> createState() => _UsersPageState();
}

class _UsersPageState extends ConsumerState<UsersPage> {
  String _search = '';
  String? _filterRole;
  List<Map<String, dynamic>> _users = [];
  bool _loading = true;
  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() => _loading = true);
    final repo = ref.read(adminRepositoryProvider);
    final list = await repo.getUsers(search: _search, role: _filterRole);
    if (mounted)
      setState(() {
        _users = list;
        _loading = false;
      });
  }

  @override
  Widget build(BuildContext context) {
    final myRole = ref.watch(currentAppRoleProvider).valueOrNull;
    final isOwner = myRole == AppRole.owner;
    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            children: [
              CupertinoSearchTextField(
                  placeholder: 'Search email',
                  onChanged: (v) => _search = v,
                  onSubmitted: (_) => _load()),
              const SizedBox(height: 8),
              SingleChildScrollView(
                  scrollDirection: Axis.horizontal,
                  child: Row(children: [
                    ChoiceChip(
                        label: const Text('All'),
                        selected: _filterRole == null,
                        onSelected: (_) => setState(() => _filterRole = null)),
                    ...AppRole.values.map((r) => Padding(
                        padding: const EdgeInsets.only(left: 8),
                        child: ChoiceChip(
                            label: Text(r.value),
                            selected: _filterRole == r.value,
                            onSelected: (_) =>
                                setState(() => _filterRole = r.value)))),
                    ElevatedButton(
                        onPressed: _load, child: const Text('Filter'))
                  ])),
            ],
          ),
        ),
        Expanded(
          child: _loading
              ? const Center(child: CupertinoActivityIndicator())
              : ListView.builder(
                  padding: const EdgeInsets.symmetric(horizontal: 16),
                  itemCount: _users.length,
                  itemBuilder: (_, i) {
                    final u = _users[i];
                    final role = AppRole.fromString(u['role'] as String?) ??
                        AppRole.free;
                    final banned = u['is_banned'] as bool? ?? false;
                    return GlassCard(
                      margin: const EdgeInsets.only(bottom: 8),
                      padding: const EdgeInsets.all(12),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(children: [
                            Expanded(
                                child: Text(u['email'] ?? '',
                                    style: const TextStyle(
                                        fontWeight: FontWeight.w600))),
                            Container(
                                padding: const EdgeInsets.symmetric(
                                    horizontal: 8, vertical: 2),
                                decoration: BoxDecoration(
                                    color: banned
                                        ? Colors.red.withOpacity(0.15)
                                        : AppColors.azureMistDeep
                                            .withOpacity(0.15),
                                    borderRadius: BorderRadius.circular(8)),
                                child: Text(banned ? 'banned' : role.value,
                                    style: TextStyle(
                                        fontSize: 11,
                                        color: banned
                                            ? Colors.red
                                            : AppColors.azureMistDeep)))
                          ]),
                          Text(u['id'] as String,
                              style: const TextStyle(
                                  fontSize: 10,
                                  color: AppColors.textSecondary)),
                          const SizedBox(height: 8),
                          Wrap(
                            spacing: 8,
                            children: [
                              ElevatedButton(
                                  onPressed: () async {
                                    await ref
                                        .read(adminRepositoryProvider)
                                        .setUserBanned(
                                            u['id'] as String, !banned);
                                    _load();
                                  },
                                  child: Text(banned ? 'Unban' : 'Ban')),
                              PopupMenuButton<String>(
                                onSelected: (newRole) async {
                                  if (!isOwner &&
                                      (newRole == 'admin' ||
                                          newRole == 'owner')) {
                                    ScaffoldMessenger.of(context).showSnackBar(
                                        const SnackBar(
                                            content: Text(
                                                'Only owner can promote to admin/owner')));
                                    return;
                                  }
                                  try {
                                    await ref
                                        .read(adminRepositoryProvider)
                                        .setUserRole(
                                            u['id'] as String, newRole);
                                    _load();
                                  } catch (e) {
                                    if (mounted)
                                      ScaffoldMessenger.of(context)
                                          .showSnackBar(SnackBar(
                                              content: Text('Failed: $e')));
                                  }
                                },
                                itemBuilder: (_) => AppRole.values
                                    .map((r) => PopupMenuItem(
                                        value: r.value,
                                        child: Text('Set ${r.value}')))
                                    .toList(),
                                child: const Chip(label: Text('Change role')),
                              ),
                            ],
                          ),
                        ],
                      ),
                    );
                  },
                ),
        ),
      ],
    );
  }
}
