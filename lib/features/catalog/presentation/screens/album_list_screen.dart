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
import '../../models/album.dart';
import '../../providers/catalog_providers.dart';

/// Daftar album + pencarian + aksi tambah/edit/hapus (staff/admin/owner).
class AlbumListScreen extends ConsumerStatefulWidget {
  const AlbumListScreen({super.key});

  @override
  ConsumerState<AlbumListScreen> createState() => _AlbumListScreenState();
}

class _AlbumListScreenState extends ConsumerState<AlbumListScreen> {
  Future<void> _confirmDelete(Album album) async {
    final confirmed = await showCupertinoDialog<bool>(
      context: context,
      builder: (context) => CupertinoAlertDialog(
        title: const Text('Hapus Album?'),
        content: Text(
          'Album "${album.title}" akan dihapus permanen. Lagu di dalamnya tidak ikut terhapus.',
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
      await ref.read(albumsControllerProvider.notifier).remove(album.id);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Album "${album.title}" dihapus')),
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
    final albumsState = ref.watch(albumsControllerProvider);

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
                title: const Text('Album'),
                leading: IconButton(
                  icon: const Icon(CupertinoIcons.chevron_left),
                  onPressed: () => context.go(AppRoutes.catalogManagement),
                ),
                actions: [
                  IconButton(
                    icon: const Icon(CupertinoIcons.add),
                    tooltip: 'Tambah Album',
                    onPressed: () => context.go(AppRoutes.catalogAlbumAdd),
                  ),
                ],
              ),
              GlassSearchBar(
                hintText: 'Cari album...',
                onChanged: (value) {
                  ref
                      .read(albumsControllerProvider.notifier)
                      .load(search: value);
                },
              ),
              Expanded(
                child: albumsState.when(
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
                                .read(albumsControllerProvider.notifier)
                                .refresh(),
                          ),
                        ],
                      ),
                    ),
                  ),
                  data: (albums) {
                    if (albums.isEmpty) {
                      return const _EmptyState();
                    }
                    return ListView.builder(
                      padding: const EdgeInsets.fromLTRB(20, 8, 20, 100),
                      itemCount: albums.length,
                      itemBuilder: (context, index) {
                        final album = albums[index];
                        return Padding(
                          padding: const EdgeInsets.only(bottom: 10),
                          child: _AlbumTile(
                            album: album,
                            onEdit: () => context.go(
                              AppRoutes.catalogAlbumEdit.replaceFirst(
                                ':id',
                                album.id,
                              ),
                            ),
                            onDelete: () => _confirmDelete(album),
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
    );
  }
}

class _AlbumTile extends StatelessWidget {
  const _AlbumTile({
    required this.album,
    required this.onEdit,
    required this.onDelete,
  });

  final Album album;
  final VoidCallback onEdit;
  final VoidCallback onDelete;

  String get _subtitle {
    final parts = <String>[
      if (album.artistName != null && album.artistName!.isNotEmpty)
        album.artistName!,
      if (album.releaseDate != null) '${album.releaseDate!.year}',
      if (album.songCount != null && album.songCount! > 0)
        '${album.songCount} lagu',
    ];
    return parts.join(' · ');
  }

  @override
  Widget build(BuildContext context) {
    return GlassCard(
      borderRadius: 16,
      padding: const EdgeInsets.all(12),
      child: Row(
        children: [
          SignedImage(
            value: album.coverUrl,
            width: 56,
            height: 56,
            borderRadius: 10,
            fallbackIcon: CupertinoIcons.music_albums,
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  album.title,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    fontSize: 15,
                    fontWeight: FontWeight.w600,
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  _subtitle.isEmpty ? 'Belum ada info' : _subtitle,
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
            CupertinoIcons.music_albums,
            size: 48,
            color: AppColors.textSecondary,
          ),
          const SizedBox(height: 12),
          Text(
            'Belum ada album',
            style: Theme.of(context).textTheme.titleMedium,
          ),
          const SizedBox(height: 4),
          const Text(
            'Tekan ikon + untuk menambah album',
            style: TextStyle(color: AppColors.textSecondary),
          ),
        ],
      ),
    );
  }
}
