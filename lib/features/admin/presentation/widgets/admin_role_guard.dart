import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../../core/models/app_role.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/widgets/glass_card.dart';
import '../../../catalog/providers/role_provider.dart';

class AdminRoleGuard extends ConsumerWidget {
  const AdminRoleGuard({super.key, required this.allowed, required this.child});
  final List<AppRole> allowed;
  final Widget child;
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final role = ref.watch(currentAppRoleProvider).valueOrNull;
    if (role == null)
      return const Scaffold(body: Center(child: CupertinoActivityIndicator()));
    if (!allowed.contains(role)) {
      return Scaffold(
        body: DecoratedBox(
          decoration: BoxDecoration(
              gradient: Theme.of(context).brightness == Brightness.dark
                  ? AppColors.darkBackgroundGradient
                  : AppColors.lightBackgroundGradient),
          child: Center(
              child: GlassCard(
                  margin: const EdgeInsets.all(24),
                  padding: const EdgeInsets.all(24),
                  child: Column(mainAxisSize: MainAxisSize.min, children: [
                    const Icon(CupertinoIcons.lock_shield,
                        size: 40, color: AppColors.accentError),
                    const SizedBox(height: 12),
                    Text('Akses ditolak',
                        style: Theme.of(context).textTheme.titleLarge),
                    const SizedBox(height: 8),
                    Text('Role ${role.value} tidak berhak akses halaman ini.',
                        textAlign: TextAlign.center,
                        style: const TextStyle(color: AppColors.textSecondary)),
                    const SizedBox(height: 16),
                    ElevatedButton(
                        onPressed: () => context.go('/main'),
                        child: const Text('Kembali'))
                  ]))),
        ),
      );
    }
    return child;
  }
}
