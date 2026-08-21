import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/theme/app_colors.dart';
import '../../../../core/widgets/glass_card.dart';
import '../../../../core/widgets/signed_image.dart';
import '../../../catalog/models/song.dart';
import '../../../catalog/providers/catalog_providers.dart';
import '../../../player/providers/player_provider.dart';

/// Artist detail page — info, top tracks, discography, follow/unfollow.
/// Takes artistId and fetches data from Supabase.
class ArtistDetailPage extends ConsumerWidget {
  const ArtistDetailPage({super.key, required this.artistId});

  final String artistId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final artists = ref.watch(artistsControllerProvider).valueOrNull ?? [];
    final artist = artists.firstWhere(
      (a) => a.id == artistId,
      orElse: () => artists.first,
    );
    final topTracksAsync = ref.watch(artistTopTracksProvider(artistId));
    final albumsAsync = ref.watch(artistAlbumsProvider(artistId));
    final followerCountAsync = ref.watch(artistFollowerCountProvider(artistId));
    final isFollowingAsync = ref.watch(isFollowingArtistProvider(artistId));

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
              // Header with back button.
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

              // Artist info.
              SliverToBoxAdapter(
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 32),
                  child: Column(
                    children: [
                      ClipOval(
                        child: SignedImage(
                          value: artist.imageUrl,
                          width: 120,
                          height: 120,
                          fallbackIcon: CupertinoIcons.person_fill,
                        ),
                      ),
                      const SizedBox(height: 16),
                      Text(
                        artist.name,
                        style: Theme.of(context)
                            .textTheme
                            .headlineMedium
                            ?.copyWith(fontWeight: FontWeight.w700),
                        textAlign: TextAlign.center,
                      ),
                      const SizedBox(height: 8),
                      followerCountAsync.when(
                        data: (count) => Text(
                          '$count followers',
                          style: Theme.of(context)
                              .textTheme
                              .bodyMedium
                              ?.copyWith(color: AppColors.textSecondary),
                        ),
                        loading: () => const SizedBox(height: 16),
                        error: (_, __) => const SizedBox.shrink(),
                      ),
                      const SizedBox(height: 16),
                      isFollowingAsync.when(
                        data: (isFollowing) => _FollowButton(
                          artistId: artistId,
                          isFollowing: isFollowing,
                        ),
                        loading: () => const SizedBox(height: 40),
                        error: (_, __) => const SizedBox.shrink(),
                      ),
                      const SizedBox(height: 16),
                      if (artist.bio != null && artist.bio!.isNotEmpty) ...[
                        Text(
                          artist.bio!,
                          textAlign: TextAlign.center,
                          style: Theme.of(context)
                              .textTheme
                              .bodyMedium
                              ?.copyWith(color: AppColors.textSecondary),
                        ),
                        const SizedBox(height: 24),
                      ],
                    ],
                  ),
                ),
              ),

              // Top Tracks.
              SliverToBoxAdapter(
                child: topTracksAsync.when(
                  data: (tracks) {
                    if (tracks.isEmpty) return const SizedBox.shrink();
                    return Padding(
                      padding: const EdgeInsets.fromLTRB(20, 0, 20, 8),
                      child: Text(
                        'Top Tracks',
                        style: Theme.of(context).textTheme.titleLarge,
                      ),
                    );
                  },
                  loading: () => const SizedBox.shrink(),
                  error: (_, __) => const SizedBox.shrink(),
                ),
              ),
              SliverToBoxAdapter(
                child: topTracksAsync.when(
                  data: (tracks) {
                    if (tracks.isEmpty) return const SizedBox.shrink();
                    return Column(
                      children: [
                        for (var i = 0; i < tracks.length; i++)
                          Padding(
                            padding: const EdgeInsets.symmetric(
                                horizontal: 20, vertical: 4),
                            child: _TrackTile(song: tracks[i], index: i),
                          ),
                      ],
                    );
                  },
                  loading: () => const SizedBox.shrink(),
                  error: (_, __) => const SizedBox.shrink(),
                ),
              ),

              // Discography.
              SliverToBoxAdapter(
                child: albumsAsync.when(
                  data: (albums) {
                    if (albums.isEmpty) return const SizedBox.shrink();
                    return Padding(
                      padding: const EdgeInsets.fromLTRB(20, 16, 20, 8),
                      child: Text(
                        'Discography',
                        style: Theme.of(context).textTheme.titleLarge,
                      ),
                    );
                  },
                  loading: () => const SizedBox.shrink(),
                  error: (_, __) => const SizedBox.shrink(),
                ),
              ),
              SliverToBoxAdapter(
                child: albumsAsync.when(
                  data: (albums) {
                    if (albums.isEmpty) return const SizedBox.shrink();
                    return SizedBox(
                      height: 180,
                      child: ListView.separated(
                        scrollDirection: Axis.horizontal,
                        padding: const EdgeInsets.symmetric(horizontal: 20),
                        itemCount: albums.length,
                        separatorBuilder: (_, __) => const SizedBox(width: 12),
                        itemBuilder: (context, index) {
                          final album = albums[index];
                          return SizedBox(
                            width: 140,
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                ClipRRect(
                                  borderRadius: BorderRadius.circular(12),
                                  child: SignedImage(
                                    value: album.coverUrl,
                                    width: 140,
                                    height: 140,
                                    fallbackIcon: CupertinoIcons.music_albums,
                                  ),
                                ),
                                const SizedBox(height: 6),
                                Text(
                                  album.title,
                                  maxLines: 1,
                                  overflow: TextOverflow.ellipsis,
                                  style: Theme.of(context).textTheme.bodyMedium,
                                ),
                              ],
                            ),
                          );
                        },
                      ),
                    );
                  },
                  loading: () => const SizedBox.shrink(),
                  error: (_, __) => const SizedBox.shrink(),
                ),
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
// FOLLOW BUTTON
// =============================================================================

class _FollowButton extends ConsumerWidget {
  const _FollowButton({
    required this.artistId,
    required this.isFollowing,
  });

  final String artistId;
  final bool isFollowing;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return SizedBox(
      width: 160,
      height: 40,
      child: OutlinedButton(
        onPressed: () async {
          final repo = ref.read(catalogRepositoryProvider);
          if (isFollowing) {
            await repo.unfollowArtist(artistId);
          } else {
            await repo.followArtist(artistId);
          }
          ref.invalidate(isFollowingArtistProvider(artistId));
          ref.invalidate(artistFollowerCountProvider(artistId));
        },
        style: OutlinedButton.styleFrom(
          foregroundColor:
              isFollowing ? AppColors.textSecondary : AppColors.azureMistDeep,
          side: BorderSide(
            color: isFollowing
                ? AppColors.textSecondary.withOpacity(0.3)
                : AppColors.azureMistDeep,
          ),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(20),
          ),
        ),
        child: Text(
          isFollowing ? 'Following' : 'Follow',
          style: const TextStyle(fontWeight: FontWeight.w600),
        ),
      ),
    );
  }
}

// =============================================================================
// TRACK TILE
// =============================================================================

class _TrackTile extends ConsumerWidget {
  const _TrackTile({required this.song, required this.index});

  final Song song;
  final int index;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final playerState = ref.watch(playerControllerProvider);
    final isActive = playerState.currentTrack?.id == song.id;

    return GlassCard(
      borderRadius: 10,
      padding: const EdgeInsets.all(8),
      child: Row(
        children: [
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
          ClipRRect(
            borderRadius: BorderRadius.circular(6),
            child: SignedImage(
              value: song.coverUrl,
              width: 40,
              height: 40,
              fallbackIcon: CupertinoIcons.music_note,
            ),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              song.title,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(
                fontSize: 14,
                fontWeight: FontWeight.w500,
                color: isActive ? AppColors.azureMistDeep : null,
              ),
            ),
          ),
          IconButton(
            onPressed: () async {
              final controller = ref.read(playerControllerProvider.notifier);
              if (isActive) {
                await controller.togglePlay();
              } else {
                await controller.playSingle(song);
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
}
