import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/constants/app_routes.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/widgets/glass_card.dart';
import '../../../../shared/providers/supabase_providers.dart';
import '../../../catalog/providers/role_provider.dart';

class ProfileTab extends ConsumerWidget {
  const ProfileTab({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final user = ref.watch(currentUserProvider);
    final email = user?.email ?? 'user@rakyzu.music';
    final role = ref.watch(currentAppRoleProvider).valueOrNull;
    final roleLabel = _roleLabel(role?.value);

    return Scaffold(
      body: DecoratedBox(
        decoration: BoxDecoration(
          gradient: isDark
              ? AppColors.darkBackgroundGradient
              : AppColors.lightBackgroundGradient,
        ),
        child: SafeArea(
          child: ListView(
            padding: const EdgeInsets.all(20),
            children: [
              Text(
                'Profile',
                style: Theme.of(context).textTheme.headlineLarge,
              ),
              const SizedBox(height: 20),
              GlassCard(
                padding: const EdgeInsets.all(24),
                child: Column(
                  children: [
                    Container(
                      width: 80,
                      height: 80,
                      decoration: BoxDecoration(
                        color: AppColors.azureMistDeep.withOpacity(0.3),
                        shape: BoxShape.circle,
                      ),
                      child: const Icon(
                        CupertinoIcons.person_fill,
                        size: 40,
                        color: AppColors.azureMistDeep,
                      ),
                    ),
                    const SizedBox(height: 12),
                    Text(
                      email,
                      style: Theme.of(context).textTheme.titleMedium,
                    ),
                    const SizedBox(height: 4),
                    Text(
                      roleLabel,
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 16),
              if (role?.canManageCatalog ?? false)
                _SettingsRow(
                  icon: CupertinoIcons.music_albums,
                  label: 'Manajemen Katalog',
                  onTap: () => context.go(AppRoutes.catalogManagement),
                ),
              if (role?.canManageCatalog ?? false)
                _SettingsRow(
                  icon: CupertinoIcons.chart_bar,
                  label: 'Admin Dashboard',
                  onTap: () => context.go(AppRoutes.admin),
                ),
              const _SettingsRow(
                icon: CupertinoIcons.settings,
                label: 'Settings',
              ),
              const _SettingsRow(
                icon: CupertinoIcons.moon,
                label: 'Dark Mode',
              ),
              const _SettingsRow(
                icon: CupertinoIcons.info_circle,
                label: 'About Rakyzu Music',
              ),
              const SizedBox(height: 24),
              GlassCard(
                borderRadius: 16,
                padding: EdgeInsets.zero,
                onTap: () async {
                  await ref.read(signOutProvider).call();
                },
                child: const Padding(
                  padding: EdgeInsets.all(16),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Icon(
                        CupertinoIcons.power,
                        color: AppColors.accentError,
                        size: 18,
                      ),
                      SizedBox(width: 8),
                      Text(
                        'Sign Out',
                        style: TextStyle(
                          color: AppColors.accentError,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _SettingsRow extends StatelessWidget {
  const _SettingsRow({required this.icon, required this.label, this.onTap});

  final IconData icon;
  final String label;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: GlassCard(
        borderRadius: 16,
        padding: const EdgeInsets.all(16),
        onTap: onTap,
        child: Row(
          children: [
            Icon(icon, color: AppColors.azureMistDeep, size: 20),
            const SizedBox(width: 12),
            Expanded(
              child: Text(label, style: Theme.of(context).textTheme.bodyLarge),
            ),
            const Icon(
              CupertinoIcons.chevron_right,
              color: AppColors.textSecondary,
              size: 18,
            ),
          ],
        ),
      ),
    );
  }
}

String _roleLabel(String? role) {
  return switch (role) {
    'premium' => 'Premium User',
    'staff' => 'Staff',
    'admin' => 'Admin',
    'owner' => 'Owner',
    _ => 'Free User',
  };
}
