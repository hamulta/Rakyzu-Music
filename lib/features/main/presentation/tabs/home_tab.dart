import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/providers/audio_player_provider.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/widgets/glass_card.dart';
import '../../../../core/widgets/signed_image.dart';
import '../../../catalog/data/r2_storage_service.dart';
import '../../../catalog/models/album.dart';
import '../../../catalog/models/artist.dart';
import '../../../catalog/models/song.dart';
import '../../../catalog/providers/catalog_providers.dart';

/// Home feed publik — menampilkan konten katalog (artis, album, lagu terbaru)
/// untuk semua user. Streaming dimulai lewat tombol play (signed URL).
class HomeTab extends ConsumerWidget {
  const HomeTab({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final artists =
        ref.watch(artistsControllerProvider).valueOrNull ?? const [];
    final albums = ref.watch(albumsControllerProvider).valueOrNull ?? const [];
    final songs = ref.watch(songsControllerProvider).valueOrNull ?? const [];

    final verifiedArtists =
        artists.where((a) => a.isVerified).toList().take(6).toList();
    final recentSongs = songs.take(10).toList();
    final recentAlbums = albums.take(6).toList();

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
              if (recentAlbums.isNotEmpty) ...[
                const _SectionHeader('Album Terbaru'),
                const SizedBox(height: 12),
                SizedBox(
                  height: 180,
                  child: ListView.separated(
                    scrollDirection: Axis.horizontal,
                    itemCount: recentAlbums.length,
                    separatorBuilder: (_, __) => const SizedBox(width: 12),
                    itemBuilder: (context, index) =>
                        _AlbumCard(album: recentAlbums[index]),
                  ),
                ),
                const SizedBox(height: 24),
              ],
              if (verifiedArtists.isNotEmpty) ...[
                const _SectionHeader('Artis Pilihan'),
                const SizedBox(height: 12),
                SizedBox(
                  height: 120,
                  child: ListView.separated(
                    scrollDirection: Axis.horizontal,
                    itemCount: verifiedArtists.length,
                    separatorBuilder: (_, __) => const SizedBox(width: 12),
                    itemBuilder: (context, index) =>
                        _ArtistCard(artist: verifiedArtists[index]),
                  ),
                ),
                const SizedBox(height: 24),
              ],
              const _SectionHeader('Lagu Terbaru'),
              const SizedBox(height: 12),
              if (recentSongs.isEmpty)
                const _EmptyFeed()
              else
                Column(
                  children: [
                    for (final song in recentSongs)
                      Padding(
                        padding: const EdgeInsets.only(bottom: 10),
                        child: _SongRow(song: song),
                      ),
                  ],
                ),
            ],
          ),
        ),
      ),
    );
  }
}

class _SectionHeader extends StatelessWidget {
  const _SectionHeader(this.title);

  final String title;

  @override
  Widget build(BuildContext context) {
    return Text(title, style: Theme.of(context).textTheme.titleLarge);
  }
}

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

class _SongRow extends ConsumerWidget {
  const _SongRow({required this.song});

  final Song song;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final audioKey = song.audioUrl;
    final isActive = ref.watch(activeAudioKeyProvider) == audioKey;

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

class _PlayToggle extends ConsumerWidget {
  const _PlayToggle({required this.song, required this.isActive});

  final Song song;
  final bool isActive;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return IconButton.filled(
      onPressed: () => _toggle(context, ref),
      style: IconButton.styleFrom(
        backgroundColor: AppColors.azureMistDeep,
        foregroundColor: Colors.white,
      ),
      icon:
          Icon(isActive ? CupertinoIcons.pause_fill : CupertinoIcons.play_fill),
    );
  }

  Future<void> _toggle(BuildContext context, WidgetRef ref) async {
    final audioKey = song.audioUrl;
    if (audioKey == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Audio belum tersedia.')),
      );
      return;
    }
    final player = ref.read(audioPlayerProvider);

    if (ref.read(activeAudioKeyProvider) == audioKey) {
      if (player.playing) {
        await player.pause();
      } else {
        await player.play();
      }
      return;
    }

    final url = await ref.read(signedAudioUrlProvider(audioKey).future);
    final ok = await playAudioSource(ref, url);
    if (ok) {
      ref.read(activeAudioKeyProvider.notifier).state = audioKey;
    } else if (context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Gagal memutar audio.')),
      );
    }
  }
}

class _EmptyFeed extends StatelessWidget {
  const _EmptyFeed();

  @override
  Widget build(BuildContext context) {
    return GlassCard(
      padding: const EdgeInsets.symmetric(vertical: 40, horizontal: 20),
      child: Column(
        children: [
          const Icon(
            CupertinoIcons.music_note,
            color: AppColors.azureMistDeep,
            size: 28,
          ),
          const SizedBox(height: 12),
          Text(
            'Belum ada lagu. Katalog akan tampil di sini.',
            textAlign: TextAlign.center,
            style: Theme.of(context).textTheme.bodyMedium,
          ),
        ],
      ),
    );
  }
}
