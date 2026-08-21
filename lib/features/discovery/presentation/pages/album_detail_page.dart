import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/theme/app_colors.dart';
import '../../../../core/widgets/glass_card.dart';
import '../../../../core/widgets/signed_image.dart';
import '../../../catalog/models/song.dart';
import '../../../catalog/providers/catalog_providers.dart';
import '../../../player/providers/player_provider.dart';

/// Album detail page — cover, info, tracklist, play all/shuffle + reorder.
class AlbumDetailPage extends ConsumerWidget {
  const AlbumDetailPage({super.key, required this.albumId});

  final String albumId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final albums = ref.watch(albumsControllerProvider).valueOrNull ?? [];
    final album = albums.firstWhere(
      (a) => a.id == albumId,
      orElse: () => albums.first,
    );
    final tracksAsync = ref.watch(albumTracksProvider(albumId));

    return Scaffold(
      body: DecoratedBox(
        decoration: BoxDecoration(
          gradient: isDark
              ? AppColors.darkBackgroundGradient
              : AppColors.lightBackgroundGradient,
        ),
        child: SafeArea(
          child: CustomScrollView(
            slivers: [
              // Header.
              SliverToBoxAdapter(
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(8, 8, 16, 0),
                  child: Row(
                    children: [
                      IconButton(
                        onPressed: () => Navigator.of(context).pop(),
                        icon: const Icon(CupertinoIcons.back),
                      ),
                      const Spacer(),
                    ],
                  ),
                ),
              ),

              // Album info.
              SliverToBoxAdapter(
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 32),
                  child: Column(
                    children: [
                      // Cover art.
                      ClipRRect(
                        borderRadius: BorderRadius.circular(16),
                        child: SignedImage(
                          value: album.coverUrl,
                          width: 200,
                          height: 200,
                          fallbackIcon: CupertinoIcons.music_albums,
                        ),
                      ),
                      const SizedBox(height: 16),
                      // Album title.
                      Text(
                        album.title,
                        style: Theme.of(context)
                            .textTheme
                            .headlineMedium
                            ?.copyWith(fontWeight: FontWeight.w700),
                        textAlign: TextAlign.center,
                      ),
                      const SizedBox(height: 8),
                      // Artist name.
                      if (album.artistName != null)
                        Text(
                          album.artistName!,
                          style: Theme.of(context)
                              .textTheme
                              .bodyLarge
                              ?.copyWith(color: AppColors.textSecondary),
                        ),
                      const SizedBox(height: 8),
                      // Release year and genre.
                      Row(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          if (album.releaseDate != null) ...[
                            Text(
                              '${album.releaseDate!.year}',
                              style: Theme.of(context)
                                  .textTheme
                                  .bodyMedium
                                  ?.copyWith(color: AppColors.textSecondary),
                            ),
                            if (album.genre != null) ...[
                              const Text(
                                ' · ',
                                style: TextStyle(
                                  color: AppColors.textSecondary,
                                ),
                              ),
                              Text(
                                album.genre!,
                                style: Theme.of(context)
                                    .textTheme
                                    .bodyMedium
                                    ?.copyWith(color: AppColors.textSecondary),
                              ),
                            ],
                          ] else if (album.genre != null)
                            Text(
                              album.genre!,
                              style: Theme.of(context)
                                  .textTheme
                                  .bodyMedium
                                  ?.copyWith(color: AppColors.textSecondary),
                            ),
                        ],
                      ),
                      const SizedBox(height: 16),
                      // Play All / Shuffle buttons.
                      tracksAsync.when(
                        data: (tracks) {
                          if (tracks.isEmpty) return const SizedBox.shrink();
                          return Row(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              _PlayAllButton(tracks: tracks),
                              const SizedBox(width: 12),
                              _ShuffleButton(tracks: tracks),
                            ],
                          );
                        },
                        loading: () => const SizedBox(height: 48),
                        error: (_, __) => const SizedBox.shrink(),
                      ),
                      const SizedBox(height: 24),
                    ],
                  ),
                ),
              ),

              // Tracklist.
              SliverToBoxAdapter(
                child: tracksAsync.when(
                  data: (tracks) {
                    if (tracks.isEmpty) {
                      return const Padding(
                        padding: EdgeInsets.all(32),
                        child: Center(
                          child: Text('No tracks in this album'),
                        ),
                      );
                    }

                    // Calculate total duration.
                    final totalSeconds = tracks.fold<int>(
                      0,
                      (sum, t) => sum + (t.durationSeconds ?? 0),
                    );
                    final totalMinutes = (totalSeconds / 60).floor();

                    return Padding(
                      padding: const EdgeInsets.fromLTRB(20, 0, 20, 8),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            '${tracks.length} tracks · $totalMinutes min',
                            style: Theme.of(context)
                                .textTheme
                                .bodyMedium
                                ?.copyWith(color: AppColors.textSecondary),
                          ),
                          const SizedBox(height: 12),
                        ],
                      ),
                    );
                  },
                  loading: () => const SizedBox.shrink(),
                  error: (_, __) => const SizedBox.shrink(),
                ),
              ),

              // Track list — drag-drop reorder (staff/admin/owner).
              tracksAsync.when(
                data: (tracks) {
                  if (tracks.isEmpty) return const SliverToBoxAdapter();
                  return SliverReorderableList(
                    itemCount: tracks.length,
                    onReorder: (oldIndex, newIndex) async {
                      if (newIndex > oldIndex) newIndex -= 1;
                      final reordered = List<Song>.from(tracks);
                      final moved = reordered.removeAt(oldIndex);
                      reordered.insert(newIndex, moved);
                      try {
                        await ref
                            .read(catalogRepositoryProvider)
                            .reorderAlbumTracks(
                              albumId,
                              reordered
                                  .asMap()
                                  .entries
                                  .map((e) =>
                                      (id: e.value.id, trackNumber: e.key + 1))
                                  .toList(),
                            );
                        ref.invalidate(albumTracksProvider(albumId));
                      } catch (e) {
                        if (context.mounted)
                          ScaffoldMessenger.of(context).showSnackBar(
                              SnackBar(content: Text('Reorder failed: $e')));
                      }
                    },
                    itemBuilder: (context, index) => Padding(
                      key: ValueKey(tracks[index].id),
                      padding: const EdgeInsets.symmetric(
                          horizontal: 20, vertical: 4),
                      child: _TrackTile(
                          song: tracks[index], index: index, allTracks: tracks),
                    ),
                  );
                },
                loading: () => const SliverToBoxAdapter(
                    child: Center(child: CupertinoActivityIndicator())),
                error: (e, _) =>
                    SliverToBoxAdapter(child: Center(child: Text('Error: $e'))),
              ),

              const SliverToBoxAdapter(child: SizedBox(height: 100)),
            ],
          ),
        ),
      ),
    );
  }
}

// =============================================================================
// PLAY ALL BUTTON
// =============================================================================

class _PlayAllButton extends ConsumerWidget {
  const _PlayAllButton({required this.tracks});

  final List<Song> tracks;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return SizedBox(
      width: 160,
      height: 44,
      child: ElevatedButton.icon(
        onPressed: () async {
          final controller = ref.read(playerControllerProvider.notifier);
          await controller.playFromQueue(tracks, startIndex: 0);
        },
        style: ElevatedButton.styleFrom(
          backgroundColor: AppColors.azureMistDeep,
          foregroundColor: Colors.white,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(22),
          ),
        ),
        icon: const Icon(CupertinoIcons.play_fill, size: 18),
        label: const Text(
          'Play All',
          style: TextStyle(fontWeight: FontWeight.w600),
        ),
      ),
    );
  }
}

// =============================================================================
// SHUFFLE BUTTON
// =============================================================================

class _ShuffleButton extends ConsumerWidget {
  const _ShuffleButton({required this.tracks});

  final List<Song> tracks;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return SizedBox(
      width: 160,
      height: 44,
      child: OutlinedButton.icon(
        onPressed: () async {
          final controller = ref.read(playerControllerProvider.notifier);
          final currentState = ref.read(playerControllerProvider);
          if (!currentState.shuffleEnabled) {
            controller.toggleShuffle();
          }
          await controller.playFromQueue(tracks, startIndex: 0);
        },
        style: OutlinedButton.styleFrom(
          foregroundColor: AppColors.textPrimary,
          side: BorderSide(color: AppColors.textSecondary.withOpacity(0.3)),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(22),
          ),
        ),
        icon: const Icon(CupertinoIcons.shuffle, size: 18),
        label: const Text(
          'Shuffle',
          style: TextStyle(fontWeight: FontWeight.w600),
        ),
      ),
    );
  }
}

// =============================================================================
// TRACK TILE
// =============================================================================

class _TrackTile extends ConsumerWidget {
  const _TrackTile({
    required this.song,
    required this.index,
    required this.allTracks,
  });

  final Song song;
  final int index;
  final List<Song> allTracks;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final playerState = ref.watch(playerControllerProvider);
    final isActive = playerState.currentTrack?.id == song.id;

    return GlassCard(
      borderRadius: 10,
      padding: const EdgeInsets.all(8),
      child: Row(
        children: [
          // Track number.
          SizedBox(
            width: 24,
            child: Text(
              '${index + 1}',
              textAlign: TextAlign.center,
              style: TextStyle(
                fontSize: 14,
                fontWeight: FontWeight.w600,
                color: isActive
                    ? AppColors.azureMistDeep
                    : AppColors.textSecondary,
              ),
            ),
          ),
          const SizedBox(width: 8),
          // Cover (if different from album).
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
                    fontWeight: FontWeight.w500,
                    color: isActive ? AppColors.azureMistDeep : null,
                  ),
                ),
                if (song.artistName != null)
                  Text(
                    song.artistName!,
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
          // Duration.
          if (song.durationSeconds != null)
            Padding(
              padding: const EdgeInsets.only(right: 8),
              child: Text(
                _formatDuration(song.durationSeconds!),
                style: const TextStyle(
                  fontSize: 12,
                  color: AppColors.textSecondary,
                ),
              ),
            ),
          // Play button.
          IconButton(
            onPressed: () async {
              final controller = ref.read(playerControllerProvider.notifier);
              if (isActive) {
                await controller.togglePlay();
              } else {
                await controller.playFromQueue(allTracks, startIndex: index);
              }
            },
            icon: Icon(
              isActive && playerState.isPlaying
                  ? CupertinoIcons.pause_fill
                  : CupertinoIcons.play_fill,
              size: 18,
              color: AppColors.textSecondary,
            ),
          ),
        ],
      ),
    );
  }

  String _formatDuration(int seconds) {
    final m = (seconds / 60).floor();
    final s = seconds % 60;
    return '$m:${s.toString().padLeft(2, '0')}';
  }
}
