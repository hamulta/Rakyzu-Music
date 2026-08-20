import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/constants/app_routes.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/widgets/glass_app_bar.dart';
import '../../../../core/widgets/glass_card.dart';
import '../../../../core/widgets/signed_audio_player.dart';
import '../../../../core/widgets/signed_image.dart';
import '../../data/catalog_exception.dart';
import '../../models/song.dart';
import '../../providers/catalog_providers.dart';

/// Detail album: daftar track + drag-drop reorder urutan.
class AlbumDetailScreen extends ConsumerStatefulWidget {
  const AlbumDetailScreen({super.key, required this.albumId});

  final String albumId;

  @override
  ConsumerState<AlbumDetailScreen> createState() => _AlbumDetailScreenState();
}

class _AlbumDetailScreenState extends ConsumerState<AlbumDetailScreen> {
  List<Song> _songs = [];
  bool _loading = true;
  bool _reordering = false;

  @override
  void initState() {
    super.initState();
    _loadSongs();
  }

  Future<void> _loadSongs() async {
    setState(() => _loading = true);
    try {
      final allSongs =
          ref.read(songsControllerProvider).valueOrNull ?? const [];
      _songs = allSongs.where((s) => s.albumId == widget.albumId).toList()
        ..sort((a, b) => a.trackNumber.compareTo(b.trackNumber));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _saveOrder() async {
    setState(() => _reordering = true);
    try {
      final tracks = <({String id, int trackNumber})>[];
      for (var i = 0; i < _songs.length; i++) {
        tracks.add((id: _songs[i].id, trackNumber: i + 1));
      }
      await ref
          .read(catalogRepositoryProvider)
          .reorderAlbumTracks(widget.albumId, tracks);

      // update local state
      for (var i = 0; i < _songs.length; i++) {
        _songs[i] = _songs[i].copyWith(trackNumber: i + 1);
      }

      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Urutan track tersimpan')),
      );
    } on CatalogException catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Gagal menyimpan: ${e.message}')),
      );
    } finally {
      if (mounted) setState(() => _reordering = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final albums = ref.watch(albumsControllerProvider).valueOrNull ?? const [];
    final album = albums.firstWhere(
      (a) => a.id == widget.albumId,
      orElse: () => albums.first,
    );

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
                title: Text(album.title),
                leading: IconButton(
                  icon: const Icon(CupertinoIcons.chevron_left),
                  onPressed: () => context.go(AppRoutes.catalogAlbums),
                ),
                actions: [
                  if (_songs.isNotEmpty)
                    IconButton(
                      icon: _reordering
                          ? const SizedBox(
                              width: 18,
                              height: 18,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            )
                          : const Icon(CupertinoIcons.sort_down),
                      tooltip: 'Simpan Urutan',
                      onPressed: _reordering ? null : _saveOrder,
                    ),
                ],
              ),
              Expanded(
                child: _loading
                    ? const Center(child: CircularProgressIndicator())
                    : _songs.isEmpty
                        ? const _EmptyTracks()
                        : ReorderableListView.builder(
                            padding: const EdgeInsets.fromLTRB(20, 8, 20, 100),
                            itemCount: _songs.length,
                            onReorder: _onReorder,
                            itemBuilder: (context, index) {
                              final song = _songs[index];
                              return Padding(
                                key: ValueKey(song.id),
                                padding: const EdgeInsets.only(bottom: 8),
                                child: _TrackTile(
                                  song: song,
                                  position: index + 1,
                                ),
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

  void _onReorder(int oldIndex, int newIndex) {
    setState(() {
      var newIdx = newIndex;
      if (newIdx > oldIndex) newIdx -= 1;
      final item = _songs.removeAt(oldIndex);
      _songs.insert(newIdx, item);
    });
  }
}

class _TrackTile extends StatelessWidget {
  const _TrackTile({required this.song, required this.position});

  final Song song;
  final int position;

  @override
  Widget build(BuildContext context) {
    return GlassCard(
      borderRadius: 14,
      padding: const EdgeInsets.all(10),
      child: Row(
        children: [
          ReorderableDragStartListener(
            index: position - 1,
            child: const Icon(
              CupertinoIcons.line_horizontal_3,
              color: AppColors.textSecondary,
              size: 20,
            ),
          ),
          const SizedBox(width: 10),
          Text(
            '$position',
            style: const TextStyle(
              fontSize: 14,
              fontWeight: FontWeight.w600,
              color: AppColors.textSecondary,
            ),
            textAlign: TextAlign.center,
          ),
          const SizedBox(width: 10),
          ClipRRect(
            borderRadius: BorderRadius.circular(8),
            child: SignedImage(
              value: song.coverUrl,
              width: 44,
              height: 44,
              fallbackIcon: CupertinoIcons.music_note,
            ),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  song.title,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.w600,
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  song.artistName ?? 'Unknown',
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
          if (song.audioUrl != null)
            SizedBox(
              width: 48,
              height: 48,
              child: SignedAudioPlayer(
                audioKey: song.audioUrl,
                title: song.title,
              ),
            ),
        ],
      ),
    );
  }
}

class _EmptyTracks extends StatelessWidget {
  const _EmptyTracks();

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Icon(
            CupertinoIcons.music_note_list,
            size: 48,
            color: AppColors.textSecondary,
          ),
          const SizedBox(height: 12),
          Text(
            'Belum ada track di album ini',
            style: Theme.of(context).textTheme.titleMedium,
          ),
          const SizedBox(height: 4),
          const Text(
            'Tambah lagu melalui menu Kelola Lagu',
            style: TextStyle(color: AppColors.textSecondary),
          ),
        ],
      ),
    );
  }
}
