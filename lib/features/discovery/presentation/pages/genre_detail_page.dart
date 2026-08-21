import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/theme/app_colors.dart';
import '../../../../core/widgets/glass_card.dart';
import '../../../../core/widgets/signed_image.dart';
import '../../../catalog/models/song.dart';
import '../../../catalog/providers/catalog_providers.dart';
import '../../../player/providers/player_provider.dart';

/// Genre detail page — shows songs filtered by genre name.
class GenreDetailPage extends ConsumerWidget {
  const GenreDetailPage({super.key, required this.genreName});

  final String genreName;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final songsAsync = ref.watch(songsByGenreProvider(genreName));

    return Scaffold(
      body: DecoratedBox(
        decoration: BoxDecoration(
          gradient: isDark
              ? AppColors.darkBackgroundGradient
              : AppColors.lightBackgroundGradient,
        ),
        child: SafeArea(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Header.
              Padding(
                padding: const EdgeInsets.fromLTRB(20, 16, 20, 8),
                child: Row(
                  children: [
                    IconButton(
                      onPressed: () => Navigator.of(context).pop(),
                      icon: const Icon(CupertinoIcons.back),
                    ),
                    Expanded(
                      child: Text(
                        genreName,
                        style: Theme.of(context).textTheme.headlineMedium,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 8),
              // Song list.
              Expanded(
                child: songsAsync.when(
                  data: (songs) {
                    if (songs.isEmpty) {
                      return Center(
                        child: Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            const Icon(
                              CupertinoIcons.music_note,
                              size: 48,
                              color: AppColors.textSecondary,
                            ),
                            const SizedBox(height: 12),
                            Text(
                              'No songs in this genre yet',
                              style: Theme.of(context).textTheme.bodyMedium,
                            ),
                          ],
                        ),
                      );
                    }
                    return ListView.builder(
                      padding: const EdgeInsets.symmetric(horizontal: 20),
                      itemCount: songs.length,
                      itemBuilder: (context, index) =>
                          _GenreSongTile(song: songs[index]),
                    );
                  },
                  loading: () => const Center(
                    child: CupertinoActivityIndicator(),
                  ),
                  error: (e, _) => Center(
                    child: Text('Error: $e'),
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

class _GenreSongTile extends ConsumerWidget {
  const _GenreSongTile({required this.song});

  final Song song;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final playerState = ref.watch(playerControllerProvider);
    final isActive = playerState.currentTrack?.id == song.id;

    return GlassCard(
      borderRadius: 12,
      padding: const EdgeInsets.all(10),
      margin: const EdgeInsets.only(bottom: 8),
      child: Row(
        children: [
          ClipRRect(
            borderRadius: BorderRadius.circular(8),
            child: SignedImage(
              value: song.coverUrl,
              width: 48,
              height: 48,
              fallbackIcon: CupertinoIcons.music_note,
            ),
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
                  style: TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.w600,
                    color: isActive ? AppColors.azureMistDeep : null,
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  [song.artistName, song.albumTitle]
                      .where((s) => s != null && s.isNotEmpty)
                      .join(' · '),
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
          if (song.playCount > 0)
            Padding(
              padding: const EdgeInsets.only(right: 8),
              child: Text(
                _formatCount(song.playCount),
                style: const TextStyle(
                  fontSize: 11,
                  color: AppColors.textSecondary,
                ),
              ),
            ),
          _PlayButton(song: song, isActive: isActive),
        ],
      ),
    );
  }

  String _formatCount(int count) {
    if (count >= 1000000) return '${(count / 1000000).toStringAsFixed(1)}M';
    if (count >= 1000) return '${(count / 1000).toStringAsFixed(1)}K';
    return count.toString();
  }
}

class _PlayButton extends ConsumerWidget {
  const _PlayButton({required this.song, required this.isActive});

  final Song song;
  final bool isActive;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final playerState = ref.watch(playerControllerProvider);
    final isCurrentlyPlaying = isActive && playerState.isPlaying;

    return IconButton.filled(
      onPressed: () async {
        final audioKey = song.audioUrl;
        if (audioKey == null) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('Audio belum tersedia.')),
          );
          return;
        }

        final controller = ref.read(playerControllerProvider.notifier);
        if (isActive) {
          await controller.togglePlay();
        } else {
          final songs =
              ref.read(songsByGenreProvider(song.genre ?? '')).valueOrNull ??
                  [];
          if (songs.isNotEmpty) {
            final idx = songs.indexWhere((s) => s.id == song.id);
            await controller.playFromQueue(
              songs,
              startIndex: idx >= 0 ? idx : 0,
            );
          } else {
            await controller.playSingle(song);
          }
        }
      },
      style: IconButton.styleFrom(
        backgroundColor: AppColors.azureMistDeep,
        foregroundColor: Colors.white,
      ),
      icon: Icon(
        isCurrentlyPlaying
            ? CupertinoIcons.pause_fill
            : CupertinoIcons.play_fill,
        size: 18,
      ),
    );
  }
}
