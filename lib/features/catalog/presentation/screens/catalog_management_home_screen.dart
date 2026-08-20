import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/constants/app_routes.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/widgets/glass_app_bar.dart';
import '../../../../core/widgets/glass_button.dart';
import '../../../../core/widgets/glass_card.dart';
import '../../providers/catalog_providers.dart';

/// Landing screen Manajemen Katalog — pintu masuk CRUD artis/album/lagu.
class CatalogManagementHomeScreen extends ConsumerWidget {
  const CatalogManagementHomeScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final artists = ref.watch(artistsControllerProvider).valueOrNull;
    final albums = ref.watch(albumsControllerProvider).valueOrNull;
    final songs = ref.watch(songsControllerProvider).valueOrNull;

    return Scaffold(
      body: DecoratedBox(
        decoration: BoxDecoration(
          gradient: isDark
              ? AppColors.darkBackgroundGradient
              : AppColors.lightBackgroundGradient,
        ),
        child: SafeArea(
          child: Column(
            children: [
              GlassAppBar(
                title: const Text('Manajemen Katalog'),
                leading: IconButton(
                  icon: const Icon(CupertinoIcons.chevron_left),
                  onPressed: () => context.go(AppRoutes.main),
                ),
              ),
              Expanded(
                child: ListView(
                  padding: const EdgeInsets.all(20),
                  children: [
                    Row(
                      children: [
                        _StatCard(label: 'Artis', count: artists?.length ?? 0),
                        const SizedBox(width: 12),
                        _StatCard(label: 'Album', count: albums?.length ?? 0),
                        const SizedBox(width: 12),
                        _StatCard(label: 'Lagu', count: songs?.length ?? 0),
                      ],
                    ),
                    const SizedBox(height: 20),
                    GlassCard(
                      borderRadius: 16,
                      padding: EdgeInsets.zero,
                      onTap: () => context.go(AppRoutes.catalogArtists),
                      child: const Padding(
                        padding: EdgeInsets.all(18),
                        child: Row(
                          children: [
                            Icon(
                              CupertinoIcons.person_2_fill,
                              color: AppColors.azureMistDeep,
                              size: 24,
                            ),
                            SizedBox(width: 14),
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    'Artis',
                                    style: TextStyle(
                                      fontSize: 16,
                                      fontWeight: FontWeight.w600,
                                    ),
                                  ),
                                  SizedBox(height: 2),
                                  Text(
                                    'Tambah, edit, dan kelola artis',
                                    style: TextStyle(
                                      fontSize: 13,
                                      color: AppColors.textSecondary,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                            Icon(
                              CupertinoIcons.chevron_right,
                              color: AppColors.textSecondary,
                            ),
                          ],
                        ),
                      ),
                    ),
                    const SizedBox(height: 12),
                    GlassCard(
                      borderRadius: 16,
                      padding: EdgeInsets.zero,
                      onTap: () => context.go(AppRoutes.catalogAlbums),
                      child: const Padding(
                        padding: EdgeInsets.all(18),
                        child: Row(
                          children: [
                            Icon(
                              CupertinoIcons.music_albums,
                              color: AppColors.azureMistDeep,
                              size: 24,
                            ),
                            SizedBox(width: 14),
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    'Album',
                                    style: TextStyle(
                                      fontSize: 16,
                                      fontWeight: FontWeight.w600,
                                    ),
                                  ),
                                  SizedBox(height: 2),
                                  Text(
                                    'Tambah, edit, dan kelola album',
                                    style: TextStyle(
                                      fontSize: 13,
                                      color: AppColors.textSecondary,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                            Icon(
                              CupertinoIcons.chevron_right,
                              color: AppColors.textSecondary,
                            ),
                          ],
                        ),
                      ),
                    ),
                    const SizedBox(height: 12),
                    GlassCard(
                      borderRadius: 16,
                      padding: EdgeInsets.zero,
                      onTap: () => context.go(AppRoutes.catalogSongs),
                      child: const Padding(
                        padding: EdgeInsets.all(18),
                        child: Row(
                          children: [
                            Icon(
                              CupertinoIcons.music_note,
                              color: AppColors.azureMistDeep,
                              size: 24,
                            ),
                            SizedBox(width: 14),
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    'Lagu',
                                    style: TextStyle(
                                      fontSize: 16,
                                      fontWeight: FontWeight.w600,
                                    ),
                                  ),
                                  SizedBox(height: 2),
                                  Text(
                                    'Tambah, edit, dan upload audio lagu',
                                    style: TextStyle(
                                      fontSize: 13,
                                      color: AppColors.textSecondary,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                            Icon(
                              CupertinoIcons.chevron_right,
                              color: AppColors.textSecondary,
                            ),
                          ],
                        ),
                      ),
                    ),
                    const SizedBox(height: 24),
                    GlassButton(
                      label: 'Kelola Artis',
                      icon: CupertinoIcons.person_2_fill,
                      onPressed: () => context.go(AppRoutes.catalogArtists),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _StatCard extends StatelessWidget {
  const _StatCard({required this.label, required this.count});

  final String label;
  final int count;

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: GlassCard(
        borderRadius: 16,
        padding: const EdgeInsets.symmetric(vertical: 16),
        child: Column(
          children: [
            Text(
              '$count',
              style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w700),
            ),
            const SizedBox(height: 4),
            Text(
              label,
              style: const TextStyle(
                fontSize: 13,
                color: AppColors.textSecondary,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
