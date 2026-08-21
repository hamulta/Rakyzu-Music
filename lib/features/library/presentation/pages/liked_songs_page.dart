import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/theme/app_colors.dart';
import '../../../../core/widgets/glass_card.dart';
import '../../../../core/widgets/signed_image.dart';
import '../../../catalog/models/song.dart';
import '../../../catalog/providers/catalog_providers.dart';
import '../../../player/providers/player_provider.dart';
import '../widgets/add_to_playlist_sheet.dart';
import '../widgets/like_button.dart';

class LikedSongsPage extends ConsumerWidget {
  const LikedSongsPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final likedAsync = ref.watch(likedSongsProvider);

    return Scaffold(
      body: DecoratedBox(
        decoration: BoxDecoration(
            gradient: isDark
                ? AppColors.darkBackgroundGradient
                : AppColors.lightBackgroundGradient),
        child: SafeArea(
          child: Column(
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(8, 8, 8, 0),
                child: Row(
                  children: [
                    IconButton(
                        onPressed: () => context.pop(),
                        icon: const Icon(CupertinoIcons.back)),
                    Text('Liked Songs',
                        style: Theme.of(context)
                            .textTheme
                            .titleLarge
                            ?.copyWith(fontWeight: FontWeight.w700)),
                    const Spacer(),
                    likedAsync.when(
                      data: (songs) => songs.isEmpty
                          ? const SizedBox.shrink()
                          : IconButton(
                              onPressed: () async {
                                final ctrl =
                                    ref.read(playerControllerProvider.notifier);
                                await ctrl.playFromQueue(songs, startIndex: 0);
                              },
                              icon: const Icon(CupertinoIcons.play_fill,
                                  color: AppColors.azureMistDeep),
                            ),
                      loading: () => const SizedBox.shrink(),
                      error: (_, __) => const SizedBox.shrink(),
                    ),
                  ],
                ),
              ),
              Expanded(
                child: likedAsync.when(
                  data: (songs) {
                    if (songs.isEmpty) {
                      return const Center(
                          child:
                              Column(mainAxisSize: MainAxisSize.min, children: [
                        Icon(CupertinoIcons.heart,
                            size: 48, color: AppColors.textSecondary),
                        SizedBox(height: 12),
                        Text('No liked songs yet')
                      ]));
                    }
                    return ListView.builder(
                      padding: const EdgeInsets.symmetric(
                          horizontal: 16, vertical: 8),
                      itemCount: songs.length,
                      itemBuilder: (context, i) {
                        final s = songs[i];
                        return Padding(
                            padding: const EdgeInsets.only(bottom: 8),
                            child: _LikedTile(song: s, index: i, all: songs));
                      },
                    );
                  },
                  loading: () =>
                      const Center(child: CupertinoActivityIndicator()),
                  error: (e, _) => Center(child: Text('Error: $e')),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _LikedTile extends ConsumerWidget {
  const _LikedTile(
      {required this.song, required this.index, required this.all});
  final Song song;
  final int index;
  final List<Song> all;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final playerState = ref.watch(playerControllerProvider);
    final isActive = playerState.currentTrack?.id == song.id;
    return GlassCard(
      padding: const EdgeInsets.all(10),
      borderRadius: 14,
      child: Row(
        children: [
          ClipRRect(
              borderRadius: BorderRadius.circular(8),
              child: SignedImage(
                  value: song.coverUrl,
                  width: 44,
                  height: 44,
                  fallbackIcon: CupertinoIcons.music_note)),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(song.title,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                        fontWeight: FontWeight.w600,
                        color: isActive ? AppColors.azureMistDeep : null)),
                Text(song.artistName ?? '',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                        color: AppColors.textSecondary, fontSize: 12)),
              ],
            ),
          ),
          LikeButton(songId: song.id),
          IconButton(
            onPressed: () async {
              final ctrl = ref.read(playerControllerProvider.notifier);
              if (isActive) {
                await ctrl.togglePlay();
              } else {
                await ctrl.playFromQueue(all, startIndex: index);
              }
            },
            icon: Icon(
                isActive && playerState.isPlaying
                    ? CupertinoIcons.pause_fill
                    : CupertinoIcons.play_fill,
                size: 18),
          ),
          PopupMenuButton<String>(
            onSelected: (v) async {
              if (v == 'add') AddToPlaylistSheet.show(context, song);
            },
            itemBuilder: (_) => [
              const PopupMenuItem(value: 'add', child: Text('Add to playlist'))
            ],
          ),
        ],
      ),
    );
  }
}
