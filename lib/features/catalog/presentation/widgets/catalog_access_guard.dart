import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/constants/app_routes.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/widgets/glass_button.dart';
import '../../../../core/widgets/glass_card.dart';
import '../../providers/role_provider.dart';

/// Pembatas akses layar manajemen katalog — hanya staff/admin/owner.
/// Keamanan otoritatif tetap di RLS Supabase; ini untuk UX & routing.
class CatalogAccessGuard extends ConsumerWidget {
  const CatalogAccessGuard({super.key, required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final role = ref.watch(currentAppRoleProvider).valueOrNull;

    if (role == null) {
      return const Scaffold(
        body: Center(child: CircularProgressIndicator()),
      );
    }

    if (!role.canManageCatalog) {
      return Scaffold(
        body: DecoratedBox(
          decoration: BoxDecoration(
            gradient: Theme.of(context).brightness == Brightness.dark
                ? AppColors.darkBackgroundGradient
                : AppColors.lightBackgroundGradient,
          ),
          child: SafeArea(
            child: Center(
              child: GlassCard(
                borderRadius: 24,
                padding: const EdgeInsets.all(24),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    const Icon(
                      CupertinoIcons.lock_fill,
                      size: 40,
                      color: AppColors.accentError,
                    ),
                    const SizedBox(height: 16),
                    Text(
                      'Akses Terbatas',
                      style: Theme.of(context).textTheme.titleLarge,
                    ),
                    const SizedBox(height: 8),
                    const Text(
                      'Hanya Staff, Admin, atau Owner yang dapat mengelola katalog.',
                      textAlign: TextAlign.center,
                      style: TextStyle(color: AppColors.textSecondary),
                    ),
                    const SizedBox(height: 20),
                    GlassButton(
                      label: 'Kembali ke Beranda',
                      icon: CupertinoIcons.house,
                      onPressed: () => context.go(AppRoutes.main),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      );
    }

    return child;
  }
}
