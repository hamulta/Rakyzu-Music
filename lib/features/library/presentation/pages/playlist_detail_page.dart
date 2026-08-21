import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:share_plus/share_plus.dart';

import '../../../../core/theme/app_colors.dart';
import '../../../../core/widgets/glass_card.dart';
import '../../../../core/widgets/signed_image.dart';
import '../../../catalog/models/song.dart';
import '../../../player/providers/player_provider.dart';
import '../../providers/playlist_providers.dart';
import '../widgets/like_button.dart';

class PlaylistDetailPage extends ConsumerWidget {
  const PlaylistDetailPage({super.key, required this.playlistId});
  final String playlistId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final playlistAsync = ref.watch(playlistDetailProvider(playlistId));
    final songsAsync = ref.watch(playlistSongsProvider(playlistId));

    return Scaffold(
      body: DecoratedBox(
        decoration: BoxDecoration(
          gradient: isDark
              ? AppColors.darkBackgroundGradient
              : AppColors.lightBackgroundGradient,
        ),
        child: SafeArea(
          child: playlistAsync.when(
            data: (pl) {
              if (pl == null) {
                return const Center(child: Text('Playlist tidak ditemukan'));
              }
              const isOwner =
                  true; // RLS ensures only owner can edit; UI gates on isOwner but backend enforces.
              return CustomScrollView(
                slivers: [
                  SliverToBoxAdapter(
                    child: Padding(
                      padding: const EdgeInsets.fromLTRB(8, 8, 8, 0),
                      child: Row(
                        children: [
                          IconButton(
                            onPressed: () => context.pop(),
                            icon: const Icon(CupertinoIcons.back),
                          ),
                          const Spacer(),
                          if (pl.isPublic)
                            IconButton(
                              onPressed: () {
                                final link =
                                    'https://rakyzumusic.app/playlist/${pl.id}';
                                Share.share(
                                  'Check out my playlist "${pl.name}" on Rakyzu Music: $link',
                                );
                              },
                              icon: const Icon(CupertinoIcons.share),
                            ),
                          PopupMenuButton<String>(
                            onSelected: (v) async {
                              if (v == 'edit') {
                                context.push('/library/playlist/${pl.id}/edit');
                              }
                              if (v == 'delete') {
                                final ok = await showCupertinoDialog<bool>(
                                  context: context,
                                  builder: (c) => CupertinoAlertDialog(
                                    title: const Text('Delete playlist?'),
                                    content: Text('Hapus "${pl.name}"?'),
                                    actions: [
                                      CupertinoDialogAction(
                                        onPressed: () =>
                                            Navigator.pop(c, false),
                                        child: const Text('Cancel'),
                                      ),
                                      CupertinoDialogAction(
                                        isDestructiveAction: true,
                                        onPressed: () => Navigator.pop(c, true),
                                        child: const Text('Delete'),
                                      ),
                                    ],
                                  ),
                                );
                                if (ok == true) {
                                  await ref
                                      .read(playlistRepositoryProvider)
                                      .deletePlaylist(pl.id);
                                  ref.invalidate(myPlaylistsProvider);
                                  if (context.mounted) context.pop();
                                }
                              }
                            },
                            itemBuilder: (_) => [
                              const PopupMenuItem(
                                value: 'edit',
                                child: Text('Edit'),
                              ),
                              const PopupMenuItem(
                                value: 'delete',
                                child: Text('Delete'),
                              ),
                            ],
                          ),
                        ],
                      ),
                    ),
                  ),
                  SliverToBoxAdapter(
                    child: Padding(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 24,
                        vertical: 8,
                      ),
                      child: Column(
                        children: [
                          ClipRRect(
                            borderRadius: BorderRadius.circular(16),
                            child:
                                pl.coverUrl != null && pl.coverUrl!.isNotEmpty
                                    ? SignedImage(
                                        value: pl.coverUrl,
                                        width: 180,
                                        height: 180,
                                        fallbackIcon:
                                            CupertinoIcons.music_note_list,
                                      )
                                    : Container(
                                        width: 180,
                                        height: 180,
                                        color: AppColors.azureMistDeep
                                            .withOpacity(0.15),
                                        child: const Icon(
                                          CupertinoIcons.music_note_list,
                                          size: 64,
                                          color: AppColors.azureMistDeep,
                                        ),
                                      ),
                          ),
                          const SizedBox(height: 16),
                          Text(
                            pl.name,
                            style: Theme.of(context)
                                .textTheme
                                .headlineSmall
                                ?.copyWith(fontWeight: FontWeight.w800),
                            textAlign: TextAlign.center,
                          ),
                          if (pl.description != null &&
                              pl.description!.isNotEmpty) ...[
                            const SizedBox(height: 6),
                            Text(
                              pl.description!,
                              textAlign: TextAlign.center,
                              style: const TextStyle(
                                color: AppColors.textSecondary,
                              ),
                            ),
                          ],
                          const SizedBox(height: 8),
                          Text(
                            '${pl.isPublic ? 'Public' : 'Private'} playlist',
                            style: const TextStyle(
                              color: AppColors.textSecondary,
                              fontSize: 12,
                            ),
                          ),
                          const SizedBox(height: 16),
                          songsAsync.when(
                            data: (songs) {
                              if (songs.isEmpty) return const SizedBox.shrink();
                              return Row(
                                mainAxisAlignment: MainAxisAlignment.center,
                                children: [
                                  CupertinoButton.filled(
                                    padding: const EdgeInsets.symmetric(
                                      horizontal: 20,
                                      vertical: 8,
                                    ),
                                    onPressed: () async {
                                      final ctrl = ref.read(
                                        playerControllerProvider.notifier,
                                      );
                                      await ctrl.playFromQueue(
                                        songs,
                                        startIndex: 0,
                                      );
                                    },
                                    child: const Row(
                                      children: [
                                        Icon(CupertinoIcons.play_fill,
                                            size: 18),
                                        SizedBox(width: 6),
                                        Text('Play All'),
                                      ],
                                    ),
                                  ),
                                  const SizedBox(width: 12),
                                  CupertinoButton(
                                    padding: const EdgeInsets.symmetric(
                                      horizontal: 20,
                                      vertical: 8,
                                    ),
                                    color: AppColors.textSecondary
                                        .withOpacity(0.15),
                                    onPressed: () async {
                                      final list = List<Song>.from(songs)
                                        ..shuffle();
                                      final ctrl = ref.read(
                                        playerControllerProvider.notifier,
                                      );
                                      await ctrl.playFromQueue(
                                        list,
                                        startIndex: 0,
                                      );
                                    },
                                    child: const Row(
                                      children: [
                                        Icon(CupertinoIcons.shuffle, size: 18),
                                        SizedBox(width: 6),
                                        Text('Shuffle'),
                                      ],
                                    ),
                                  ),
                                ],
                              );
                            },
                            loading: () => const SizedBox.shrink(),
                            error: (_, __) => const SizedBox.shrink(),
                          ),
                          const SizedBox(height: 16),
                        ],
                      ),
                    ),
                  ),
                  songsAsync.when(
                    data: (songs) {
                      if (songs.isEmpty) {
                        return const SliverToBoxAdapter(
                          child: Padding(
                            padding: EdgeInsets.all(32),
                            child: Center(
                              child: Text(
                                'Belum ada lagu • tap + Add to Playlist dari Home/Search',
                              ),
                            ),
                          ),
                        );
                      }
                      return SliverReorderableList(
                        itemCount: songs.length,
                        onReorder: (oldIdx, newIdx) async {
                          if (newIdx > oldIdx) newIdx -= 1;
                          final ids = songs.map((e) => e.id).toList();
                          final moved = ids.removeAt(oldIdx);
                          ids.insert(newIdx, moved);
                          try {
                            await ref
                                .read(playlistRepositoryProvider)
                                .reorderPlaylistSongs(playlistId, ids);
                            ref.invalidate(playlistSongsProvider(playlistId));
                          } catch (_) {}
                        },
                        itemBuilder: (context, i) {
                          final s = songs[i];
                          return Padding(
                            key: ValueKey(s.id),
                            padding: const EdgeInsets.symmetric(
                              horizontal: 16,
                              vertical: 4,
                            ),
                            child: _PlaylistSongTile(
                              playlistId: playlistId,
                              song: s,
                              index: i,
                              songs: songs,
                              isOwner: isOwner,
                            ),
                          );
                        },
                      );
                    },
                    loading: () => const SliverToBoxAdapter(
                      child: Center(child: CupertinoActivityIndicator()),
                    ),
                    error: (e, _) => SliverToBoxAdapter(
                      child: Center(child: Text('Error: $e')),
                    ),
                  ),
                  const SliverToBoxAdapter(child: SizedBox(height: 100)),
                ],
              );
            },
            loading: () => const Center(child: CupertinoActivityIndicator()),
            error: (e, _) => Center(child: Text('Error: $e')),
          ),
        ),
      ),
    );
  }
}

class _PlaylistSongTile extends ConsumerWidget {
  const _PlaylistSongTile({
    required this.playlistId,
    required this.song,
    required this.index,
    required this.songs,
    required this.isOwner,
  });
  final String playlistId;
  final Song song;
  final int index;
  final List<Song> songs;
  final bool isOwner;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final playerState = ref.watch(playerControllerProvider);
    final isActive = playerState.currentTrack?.id == song.id;
    return GlassCard(
      padding: const EdgeInsets.all(10),
      borderRadius: 14,
      child: Row(
        children: [
          if (isOwner)
            const Icon(
              CupertinoIcons.bars,
              size: 16,
              color: AppColors.textSecondary,
            ),
          if (isOwner) const SizedBox(width: 8),
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
                  style: TextStyle(
                    fontWeight: FontWeight.w600,
                    color: isActive ? AppColors.azureMistDeep : null,
                  ),
                ),
                Text(
                  song.artistName ?? '',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    color: AppColors.textSecondary,
                    fontSize: 12,
                  ),
                ),
              ],
            ),
          ),
          LikeButton(songId: song.id, size: 20),
          const SizedBox(width: 6),
          IconButton(
            onPressed: () async {
              final ctrl = ref.read(playerControllerProvider.notifier);
              if (isActive) {
                await ctrl.togglePlay();
              } else {
                await ctrl.playFromQueue(songs, startIndex: index);
              }
            },
            icon: Icon(
              isActive && playerState.isPlaying
                  ? CupertinoIcons.pause_fill
                  : CupertinoIcons.play_fill,
              size: 18,
            ),
          ),
          if (isOwner)
            PopupMenuButton<String>(
              onSelected: (v) async {
                if (v == 'remove') {
                  await ref
                      .read(playlistRepositoryProvider)
                      .removeSongFromPlaylist(playlistId, song.id);
                  ref.invalidate(playlistSongsProvider(playlistId));
                  ref.invalidate(myPlaylistsProvider);
                }
              },
              itemBuilder: (_) => [
                const PopupMenuItem(
                  value: 'remove',
                  child: Text('Remove from playlist'),
                ),
              ],
            ),
        ],
      ),
    );
  }
}
