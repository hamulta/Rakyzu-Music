import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/theme/app_colors.dart';
import '../../../../core/widgets/glass_card.dart';
import '../../../../core/widgets/signed_image.dart';
import '../../../catalog/models/album.dart';
import '../../../catalog/models/artist.dart';
import '../../../catalog/models/song.dart';
import '../../../catalog/providers/catalog_providers.dart';
import '../../../player/providers/player_provider.dart';

/// Home feed publik — menampilkan konten katalog (artis, album, lagu terbaru)
/// untuk semua user. Streaming dimulai lewat tombol play (signed URL).
class HomeTab extends ConsumerWidget {
  const HomeTab({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final artists =
        ref.watch(artistsControllerProvider).valueOrNull ?? const [];
    final songs = ref.watch(songsControllerProvider).valueOrNull ?? const [];
    final trendingSongs = ref.watch(trendingSongsProvider).valueOrNull ?? [];
    final newReleaseAlbums =
        ref.watch(newReleaseAlbumsProvider).valueOrNull ?? [];
    final newReleaseSongs =
        ref.watch(newReleaseSongsProvider).valueOrNull ?? [];
    final recommendedSongs =
        ref.watch(recommendedSongsProvider).valueOrNull ?? [];

    final verifiedArtists =
        artists.where((a) => a.isVerified).toList().take(6).toList();

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
              Row(
                children: [
                  Expanded(
                    child: Text(
                      'Home',
                      style: Theme.of(context).textTheme.headlineLarge,
                    ),
                  ),
                  const Icon(
                    CupertinoIcons.bell,
                    color: AppColors.textSecondary,
                  ),
                ],
              ),
              const SizedBox(height: 20),

              // Trending Now — songs with highest play_count.
              if (trendingSongs.isNotEmpty) ...[
                _FeedSection<Song>(
                  title: 'Trending Now',
                  items: trendingSongs,
                  height: 180,
                  itemBuilder: (song) => _SongCard(song: song),
                ),
                const SizedBox(height: 24),
              ],

              // New Releases — albums by created_at desc.
              if (newReleaseAlbums.isNotEmpty) ...[
                _FeedSection<Album>(
                  title: 'New Releases',
                  items: newReleaseAlbums,
                  height: 180,
                  itemBuilder: (album) => _AlbumCard(album: album),
                ),
                const SizedBox(height: 24),
              ],

              // Verified artists.
              if (verifiedArtists.isNotEmpty) ...[
                _FeedSection<Artist>(
                  title: 'Artis Pilihan',
                  items: verifiedArtists,
                  height: 120,
                  itemBuilder: (artist) => _ArtistCard(artist: artist),
                ),
                const SizedBox(height: 24),
              ],

              // Made For You — personalized recommendations.
              if (recommendedSongs.isNotEmpty) ...[
                _FeedSection<Song>(
                  title: 'Made For You',
                  items: recommendedSongs,
                  height: 180,
                  itemBuilder: (song) => _SongCard(song: song),
                ),
                const SizedBox(height: 24),
              ],

              // New release songs — vertical list.
              if (newReleaseSongs.isNotEmpty) ...[
                const _SectionHeader('Lagu Terbaru'),
                const SizedBox(height: 12),
                Column(
                  children: [
                    for (final song in newReleaseSongs.take(10))
                      Padding(
                        padding: const EdgeInsets.only(bottom: 10),
                        child: _SongRow(song: song),
                      ),
                  ],
                ),
                const SizedBox(height: 24),
              ],

              // Fallback: all songs if no new releases.
              if (newReleaseSongs.isEmpty && songs.isNotEmpty) ...[
                const _SectionHeader('Semua Lagu'),
                const SizedBox(height: 12),
                Column(
                  children: [
                    for (final song in songs.take(10))
                      Padding(
                        padding: const EdgeInsets.only(bottom: 10),
                        child: _SongRow(song: song),
                      ),
                  ],
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}

// =============================================================================
// REUSABLE FEED SECTION
// =============================================================================

/// Generic horizontal scroll section — title + card list.
class _FeedSection<T> extends StatelessWidget {
  const _FeedSection({
    required this.title,
    required this.items,
    required this.itemBuilder,
    this.height = 180,
  });

  final String title;
  final List<T> items;
  final Widget Function(T item) itemBuilder;
  final double height;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _SectionHeader(title),
        const SizedBox(height: 12),
        SizedBox(
          height: height,
          child: ListView.separated(
            scrollDirection: Axis.horizontal,
            itemCount: items.length,
            separatorBuilder: (_, __) => const SizedBox(width: 12),
            itemBuilder: (context, index) => itemBuilder(items[index]),
          ),
        ),
      ],
    );
  }
}

// =============================================================================
// SECTION HEADER
// =============================================================================

class _SectionHeader extends StatelessWidget {
  const _SectionHeader(this.title);

  final String title;

  @override
  Widget build(BuildContext context) {
    return Text(title, style: Theme.of(context).textTheme.titleLarge);
  }
}

// =============================================================================
// CARD WIDGETS
// =============================================================================

class _AlbumCard extends StatelessWidget {
  const _AlbumCard({required this.album});

  final Album album;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: 140,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          ClipRRect(
            borderRadius: BorderRadius.circular(14),
            child: SignedImage(
              value: album.coverUrl,
              width: 140,
              height: 140,
              fallbackIcon: CupertinoIcons.music_albums,
            ),
          ),
          const SizedBox(height: 8),
          Text(
            album.title,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: Theme.of(context).textTheme.bodyMedium,
          ),
          if (album.artistName != null && album.artistName!.isNotEmpty)
            Text(
              album.artistName!,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: Theme.of(context).textTheme.bodySmall,
            ),
        ],
      ),
    );
  }
}

class _SongCard extends ConsumerWidget {
  const _SongCard({required this.song});

  final Song song;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return SizedBox(
      width: 140,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Stack(
            children: [
              ClipRRect(
                borderRadius: BorderRadius.circular(14),
                child: SignedImage(
                  value: song.coverUrl,
                  width: 140,
                  height: 140,
                  fallbackIcon: CupertinoIcons.music_note,
                ),
              ),
              if (song.playCount > 0)
                Positioned(
                  top: 8,
                  right: 8,
                  child: Container(
                    padding:
                        const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                    decoration: BoxDecoration(
                      color: Colors.black54,
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        const Icon(CupertinoIcons.play_fill,
                            size: 10, color: Colors.white),
                        const SizedBox(width: 2),
                        Text(
                          _formatCount(song.playCount),
                          style: const TextStyle(
                              fontSize: 10, color: Colors.white),
                        ),
                      ],
                    ),
                  ),
                ),
            ],
          ),
          const SizedBox(height: 8),
          Text(
            song.title,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: Theme.of(context).textTheme.bodyMedium,
          ),
          if (song.artistName != null && song.artistName!.isNotEmpty)
            Text(
              song.artistName!,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: Theme.of(context).textTheme.bodySmall,
            ),
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

class _ArtistCard extends StatelessWidget {
  const _ArtistCard({required this.artist});

  final Artist artist;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: 96,
      child: Column(
        children: [
          Stack(
            alignment: Alignment.center,
            children: [
              ClipOval(
                child: SignedImage(
                  value: artist.imageUrl,
                  width: 72,
                  height: 72,
                  fallbackIcon: CupertinoIcons.person_fill,
                ),
              ),
              if (artist.isVerified)
                const Positioned(
                  right: 6,
                  bottom: 6,
                  child: Icon(
                    CupertinoIcons.checkmark_seal_fill,
                    size: 20,
                    color: AppColors.azureMistDeep,
                  ),
                ),
            ],
          ),
          const SizedBox(height: 8),
          Text(
            artist.name,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: Theme.of(context).textTheme.bodySmall,
          ),
        ],
      ),
    );
  }
}

// =============================================================================
// SONG ROW (vertical list item)
// =============================================================================

class _SongRow extends ConsumerWidget {
  const _SongRow({required this.song});

  final Song song;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final playerState = ref.watch(playerControllerProvider);
    final isActive = playerState.currentTrack?.id == song.id;

    return GlassCard(
      borderRadius: 14,
      padding: const EdgeInsets.all(10),
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
                  style: const TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.w600,
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  _subtitle(),
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
          _PlayToggle(song: song, isActive: isActive),
        ],
      ),
    );
  }

  String _subtitle() {
    final parts = <String>[
      if (song.artistName != null && song.artistName!.isNotEmpty)
        song.artistName!,
      if (song.albumTitle != null && song.albumTitle!.isNotEmpty)
        song.albumTitle!,
    ];
    return parts.join(' · ');
  }
}

// =============================================================================
// PLAY TOGGLE
// =============================================================================

class _PlayToggle extends ConsumerWidget {
  const _PlayToggle({required this.song, required this.isActive});

  final Song song;
  final bool isActive;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final playerState = ref.watch(playerControllerProvider);
    final isCurrentlyPlaying = isActive && playerState.isPlaying;

    return IconButton.filled(
      onPressed: () => _toggle(context, ref, isCurrentlyPlaying),
      style: IconButton.styleFrom(
        backgroundColor: AppColors.azureMistDeep,
        foregroundColor: Colors.white,
      ),
      icon: Icon(
        isCurrentlyPlaying
            ? CupertinoIcons.pause_fill
            : CupertinoIcons.play_fill,
      ),
    );
  }

  Future<void> _toggle(
    BuildContext context,
    WidgetRef ref,
    bool isCurrentlyPlaying,
  ) async {
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
      return;
    }

    final songs = ref.read(songsControllerProvider).valueOrNull ?? [];
    if (songs.isNotEmpty) {
      final idx = songs.indexWhere((s) => s.id == song.id);
      await controller.playFromQueue(songs, startIndex: idx >= 0 ? idx : 0);
    } else {
      await controller.playSingle(song);
    }
  }
}
