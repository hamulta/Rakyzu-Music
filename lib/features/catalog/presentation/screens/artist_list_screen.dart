import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/constants/app_routes.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/widgets/glass_app_bar.dart';
import '../../../../core/widgets/glass_button.dart';
import '../../../../core/widgets/glass_card.dart';
import '../../../../core/widgets/glass_search_bar.dart';
import '../../../../core/widgets/signed_image.dart';
import '../../models/artist.dart';
import '../../providers/catalog_providers.dart';
import '../widgets/catalog_access_guard.dart';

/// Daftar artis + pencarian + aksi tambah/edit/hapus (staff/admin/owner).
class ArtistListScreen extends ConsumerStatefulWidget {
  const ArtistListScreen({super.key});

  @override
  ConsumerState<ArtistListScreen> createState() => _ArtistListScreenState();
}

class _ArtistListScreenState extends ConsumerState<ArtistListScreen> {
  Future<void> _confirmDelete(Artist artist) async {
    final confirmed = await showCupertinoDialog<bool>(
      context: context,
      builder: (context) => CupertinoAlertDialog(
        title: const Text('Hapus Artis?'),
        content: Text(
          'Artis "${artist.name}" akan dihapus permanen. Album & lagu terkait tidak ikut terhapus.',
        ),
        actions: [
          CupertinoDialogAction(
            onPressed: () => Navigator.of(context).pop(false),
            child: const Text('Batal'),
          ),
          CupertinoDialogAction(
            isDestructiveAction: true,
            onPressed: () => Navigator.of(context).pop(true),
            child: const Text('Hapus'),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;

    try {
      await ref.read(artistsControllerProvider.notifier).remove(artist.id);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Artis "${artist.name}" dihapus')),
      );
    } on Exception catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Gagal menghapus: $e')),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final artistsState = ref.watch(artistsControllerProvider);

    return CatalogAccessGuard(
      child: Scaffold(
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
                  title: const Text('Artis'),
                  leading: IconButton(
                    icon: const Icon(CupertinoIcons.chevron_left),
                    onPressed: () => context.go(AppRoutes.catalogManagement),
                  ),
                  actions: [
                    IconButton(
                      icon: const Icon(CupertinoIcons.add),
                      tooltip: 'Tambah Artis',
                      onPressed: () => context.go(AppRoutes.catalogArtistAdd),
                    ),
                  ],
                ),
                GlassSearchBar(
                  hintText: 'Cari artis...',
                  onChanged: (value) {
                    ref
                        .read(artistsControllerProvider.notifier)
                        .load(search: value);
                  },
                ),
                Expanded(
                  child: artistsState.when(
                    loading: () =>
                        const Center(child: CircularProgressIndicator()),
                    error: (error, _) => Center(
                      child: Padding(
                        padding: const EdgeInsets.all(24),
                        child: Column(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Text('$error', textAlign: TextAlign.center),
                            const SizedBox(height: 16),
                            GlassButton(
                              label: 'Coba Lagi',
                              onPressed: () => ref
                                  .read(artistsControllerProvider.notifier)
                                  .refresh(),
                            ),
                          ],
                        ),
                      ),
                    ),
                    data: (artists) {
                      if (artists.isEmpty) {
                        return const _EmptyState();
                      }
                      return ListView.builder(
                        padding: const EdgeInsets.fromLTRB(20, 8, 20, 100),
                        itemCount: artists.length,
                        itemBuilder: (context, index) {
                          final artist = artists[index];
                          return Padding(
                            padding: const EdgeInsets.only(bottom: 10),
                            child: _ArtistTile(
                              artist: artist,
                              onEdit: () => context.go(
                                AppRoutes.catalogArtistEdit.replaceFirst(
                                  ':id',
                                  artist.id,
                                ),
                              ),
                              onDelete: () => _confirmDelete(artist),
                            ),
                          );
                        },
                      );
                    },
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _ArtistTile extends StatelessWidget {
  const _ArtistTile({
    required this.artist,
    required this.onEdit,
    required this.onDelete,
  });

  final Artist artist;
  final VoidCallback onEdit;
  final VoidCallback onDelete;

  @override
  Widget build(BuildContext context) {
    return GlassCard(
      borderRadius: 16,
      padding: const EdgeInsets.all(12),
      child: Row(
        children: [
          SignedImage(
            value: artist.imageUrl,
            width: 56,
            height: 56,
            borderRadius: 28,
            fallbackIcon: CupertinoIcons.person_fill,
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Flexible(
                      child: Text(
                        artist.name,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(
                          fontSize: 15,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ),
                    if (artist.isVerified) ...[
                      const SizedBox(width: 6),
                      const Icon(
                        CupertinoIcons.checkmark_seal_fill,
                        size: 16,
                        color: AppColors.azureMistDeep,
                      ),
                    ],
                  ],
                ),
                const SizedBox(height: 2),
                Text(
                  artist.bio?.isNotEmpty ?? false
                      ? artist.bio!
                      : 'Belum ada bio',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    fontSize: 13,
                    color: AppColors.textSecondary,
                  ),
                ),
              ],
            ),
          ),
          IconButton(
            icon: const Icon(CupertinoIcons.pencil, size: 18),
            color: AppColors.azureMistDeep,
            onPressed: onEdit,
          ),
          IconButton(
            icon: const Icon(CupertinoIcons.trash, size: 18),
            color: AppColors.accentError,
            onPressed: onDelete,
          ),
        ],
      ),
    );
  }
}

class _EmptyState extends StatelessWidget {
  const _EmptyState();

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Icon(
            CupertinoIcons.person_2,
            size: 48,
            color: AppColors.textSecondary,
          ),
          const SizedBox(height: 12),
          Text(
            'Belum ada artis',
            style: Theme.of(context).textTheme.titleMedium,
          ),
          const SizedBox(height: 4),
          const Text(
            'Tekan ikon + untuk menambah artis',
            style: TextStyle(color: AppColors.textSecondary),
          ),
        ],
      ),
    );
  }
}
