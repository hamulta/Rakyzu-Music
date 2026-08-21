import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../catalog/providers/role_provider.dart';

class AdminShell extends ConsumerWidget {
  const AdminShell({super.key, required this.child});
  final Widget child;
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final role = ref.watch(currentAppRoleProvider).valueOrNull;
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final canAnalytics = role?.canViewAnalytics ?? false;
    final canRevenue = role?.canViewRevenue ?? false;
    final canUsers = role?.canManageUsers ?? false;
    final canPricing = role?.canManagePricing ?? false;
    final canCatalog = role?.canManageCatalog ?? false;
    return Scaffold(
      body: Row(
        children: [
          Container(
            width: 220,
            color:
                isDark ? AppColors.darkSurface : Colors.white.withOpacity(0.95),
            child: SafeArea(
              child: ListView(
                padding: const EdgeInsets.all(16),
                children: [
                  Text('Rakyzu Admin',
                      style: Theme.of(context).textTheme.titleLarge?.copyWith(
                          fontWeight: FontWeight.w800,
                          color: AppColors.azureMistDeep)),
                  Text('Role: ${role?.value ?? '-'}',
                      style: const TextStyle(
                          color: AppColors.textSecondary, fontSize: 12)),
                  const Divider(height: 32),
                  _nav(
                      context, Icons.dashboard, 'Analytics', '/admin/analytics',
                      enabled: canAnalytics),
                  _nav(context, Icons.attach_money, 'Revenue', '/admin/revenue',
                      enabled: canRevenue),
                  _nav(context, Icons.people, 'Users', '/admin/users',
                      enabled: canUsers),
                  _nav(
                      context, Icons.library_music, 'Catalog', '/admin/catalog',
                      enabled: canCatalog),
                  _nav(context, Icons.price_change, 'Pricing', '/admin/pricing',
                      enabled: canPricing),
                  _nav(context, Icons.ads_click, 'Ad Analytics', '/admin/ads',
                      enabled: canAnalytics),
                  const Divider(height: 32),
                  ListTile(
                      leading: const Icon(CupertinoIcons.back),
                      title: const Text('Back to App'),
                      onTap: () => context.go('/main')),
                ],
              ),
            ),
          ),
          const VerticalDivider(width: 1),
          Expanded(
              child: DecoratedBox(
                  decoration: BoxDecoration(
                      gradient: isDark
                          ? AppColors.darkBackgroundGradient
                          : AppColors.lightBackgroundGradient),
                  child: child)),
        ],
      ),
    );
  }

  Widget _nav(BuildContext ctx, IconData icon, String label, String route,
      {required bool enabled}) {
    final loc = GoRouterState.of(ctx).uri.toString();
    final selected = loc.startsWith(route);
    return ListTile(
      enabled: enabled,
      leading: Icon(icon,
          color: enabled
              ? (selected ? AppColors.azureMistDeep : AppColors.textSecondary)
              : Colors.grey),
      title: Text(label,
          style: TextStyle(
              color: enabled
                  ? (selected ? AppColors.azureMistDeep : null)
                  : Colors.grey,
              fontWeight: selected ? FontWeight.w700 : null)),
      selected: selected && enabled,
      onTap: enabled ? () => ctx.go(route) : null,
    );
  }
}
