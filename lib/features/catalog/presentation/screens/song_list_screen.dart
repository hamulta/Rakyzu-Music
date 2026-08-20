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
import '../../models/song.dart';
import '../../providers/catalog_providers.dart';
import '../widgets/catalog_access_guard.dart';

/// Daftar lagu + pencarian + aksi tambah/edit/hapus (staff/admin/owner).
class SongListScreen extends ConsumerStatefulWidget {
  const SongListScreen({super.key});

  @override
  ConsumerState<SongListScreen> createState() => _SongListScreenState();
}

class _SongListScreenState extends ConsumerState<SongListScreen> {
  Future<void> _confirmDelete(Song song) async {
    final confirmed = await showCupertinoDialog<bool>(
      context: context,
      builder: (context) => CupertinoAlertDialog(
        title: const Text('Hapus Lagu?'),
        content: Text(
          'Lagu "${song.title}" akan dihapus permanen.',
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
      await ref.read(songsControllerProvider.notifier).remove(song.id);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Lagu "${song.title}" dihapus')),
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
    final songsState = ref.watch(songsControllerProvider);

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
                  title: const Text('Lagu'),
                  leading: IconButton(
                    icon: const Icon(CupertinoIcons.chevron_left),
                    onPressed: () => context.go(AppRoutes.catalogManagement),
                  ),
                  actions: [
                    IconButton(
                      icon: const Icon(CupertinoIcons.add),
                      tooltip: 'Tambah Lagu',
                      onPressed: () => context.go(AppRoutes.catalogSongAdd),
                    ),
                    IconButton(
                      icon: const Icon(CupertinoIcons.doc_append),
                      tooltip: 'Bulk Upload',
                      onPressed: () => context.go(AppRoutes.catalogBulkUpload),
                    ),
                  ],
                ),
                GlassSearchBar(
                  hintText: 'Cari lagu...',
                  onChanged: (value) {
                    ref
                        .read(songsControllerProvider.notifier)
                        .load(search: value);
                  },
                ),
                Expanded(
                  child: songsState.when(
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
                                  .read(songsControllerProvider.notifier)
                                  .refresh(),
                            ),
                          ],
                        ),
                      ),
                    ),
                    data: (songs) {
                      if (songs.isEmpty) {
                        return const _EmptyState();
                      }
                      return ListView.builder(
                        padding: const EdgeInsets.fromLTRB(20, 8, 20, 100),
                        itemCount: songs.length,
                        itemBuilder: (context, index) {
                          final song = songs[index];
                          return Padding(
                            padding: const EdgeInsets.only(bottom: 10),
                            child: _SongTile(
                              song: song,
                              onEdit: () => context.go(
                                AppRoutes.catalogSongEdit.replaceFirst(
                                  ':id',
                                  song.id,
                                ),
                              ),
                              onDelete: () => _confirmDelete(song),
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

class _SongTile extends StatelessWidget {
  const _SongTile({
    required this.song,
    required this.onEdit,
    required this.onDelete,
  });

  final Song song;
  final VoidCallback onEdit;
  final VoidCallback onDelete;

  String get _subtitle {
    final parts = <String>[
      if (song.trackNumber > 0) 'Track ${song.trackNumber}',
      if (song.artistName != null && song.artistName!.isNotEmpty)
        song.artistName!,
      if (song.albumTitle != null && song.albumTitle!.isNotEmpty)
        song.albumTitle!,
      if (song.durationSeconds != null && song.durationSeconds! > 0)
        _fmtDuration(song.durationSeconds!),
    ];
    return parts.join(' · ');
  }

  String _fmtDuration(int seconds) {
    final m = (seconds ~/ 60).toString();
    final s = (seconds % 60).toString().padLeft(2, '0');
    return '$m:$s';
  }

  @override
  Widget build(BuildContext context) {
    return GlassCard(
      borderRadius: 16,
      padding: const EdgeInsets.all(12),
      child: Row(
        children: [
          SignedImage(
            value: song.coverUrl,
            width: 56,
            height: 56,
            borderRadius: 10,
            fallbackIcon: CupertinoIcons.music_note,
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  song.title,
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
            CupertinoIcons.music_note,
            size: 48,
            color: AppColors.textSecondary,
          ),
          const SizedBox(height: 12),
          Text(
            'Belum ada lagu',
            style: Theme.of(context).textTheme.titleMedium,
          ),
          const SizedBox(height: 4),
          const Text(
            'Tekan ikon + untuk menambah lagu',
            style: TextStyle(color: AppColors.textSecondary),
          ),
        ],
      ),
    );
  }
}
