import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/constants/app_routes.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/widgets/glass_card.dart';
import '../../../catalog/providers/catalog_providers.dart';
import '../widgets/admin_section_header.dart';
import '../widgets/admin_stats_section.dart';

/// Dashboard admin — stat + aksi cepat ke CRUD katalog.
/// Dilindungi CatalogAccessGuard (hanya staff/admin/owner).
class AdminDashboardScreen extends ConsumerWidget {
  const AdminDashboardScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final artists =
        ref.watch(artistsControllerProvider).valueOrNull ?? const [];
    final albums = ref.watch(albumsControllerProvider).valueOrNull ?? const [];
    final songs = ref.watch(songsControllerProvider).valueOrNull ?? const [];

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
                'Admin Dashboard',
                style: Theme.of(context).textTheme.headlineLarge,
              ),
              const SizedBox(height: 20),
              AdminStatsSection(
                artists: artists.length,
                albums: albums.length,
                songs: songs.length,
              ),
              const SizedBox(height: 24),
              AdminSectionHeader(
                title: 'Artis',
                count: artists.length,
                onViewAll: () => context.go(AppRoutes.catalogArtists),
                onAdd: () => context.go(AppRoutes.catalogArtistAdd),
              ),
              const SizedBox(height: 8),
              _QuickList(
                items: artists
                    .take(5)
                    .map(
                      (a) => _QuickItem(
                        title: a.name,
                        subtitle: a.bio ?? 'Belum ada bio',
                        icon: CupertinoIcons.person_fill,
                      ),
                    )
                    .toList(),
              ),
              const SizedBox(height: 24),
              AdminSectionHeader(
                title: 'Album',
                count: albums.length,
                onViewAll: () => context.go(AppRoutes.catalogAlbums),
                onAdd: () => context.go(AppRoutes.catalogAlbumAdd),
              ),
              const SizedBox(height: 8),
              _QuickList(
                items: albums
                    .take(5)
                    .map(
                      (a) => _QuickItem(
                        title: a.title,
                        subtitle: a.artistName ?? 'Unknown artist',
                        icon: CupertinoIcons.music_albums,
                      ),
                    )
                    .toList(),
              ),
              const SizedBox(height: 24),
              AdminSectionHeader(
                title: 'Lagu',
                count: songs.length,
                onViewAll: () => context.go(AppRoutes.catalogSongs),
                onAdd: () => context.go(AppRoutes.catalogSongAdd),
              ),
              const SizedBox(height: 8),
              _QuickList(
                items: songs
                    .take(5)
                    .map(
                      (s) => _QuickItem(
                        title: s.title,
                        subtitle: s.artistName ?? 'Unknown',
                        icon: CupertinoIcons.music_note,
                      ),
                    )
                    .toList(),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _QuickItem {
  const _QuickItem({
    required this.title,
    required this.subtitle,
    required this.icon,
  });

  final String title;
  final String subtitle;
  final IconData icon;
}

class _QuickList extends StatelessWidget {
  const _QuickList({required this.items});

  final List<_QuickItem> items;

  @override
  Widget build(BuildContext context) {
    if (items.isEmpty) {
      return const GlassCard(
        padding: EdgeInsets.all(16),
        child: Text(
          'Belum ada data',
          style: TextStyle(color: AppColors.textSecondary),
        ),
      );
    }

    return GlassCard(
      borderRadius: 16,
      padding: EdgeInsets.zero,
      child: Column(
        children: [
          for (var i = 0; i < items.length; i++) ...[
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
              child: Row(
                children: [
                  Icon(items[i].icon, size: 18, color: AppColors.azureMistDeep),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          items[i].title,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                            fontSize: 13,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                        Text(
                          items[i].subtitle,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                            fontSize: 12,
                            color: AppColors.textSecondary,
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
            if (i < items.length - 1) const Divider(height: 1, indent: 42),
          ],
        ],
      ),
    );
  }
}
