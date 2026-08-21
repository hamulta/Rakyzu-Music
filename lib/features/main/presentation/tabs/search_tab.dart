import 'dart:async';

import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/theme/app_colors.dart';
import '../../../../core/widgets/glass_card.dart';
import '../../../../core/widgets/glass_search_bar.dart';
import '../../../../core/widgets/signed_image.dart';
import '../../../catalog/models/album.dart';
import '../../../catalog/models/artist.dart';
import '../../../catalog/models/song.dart';
import '../../../catalog/providers/catalog_providers.dart';

/// Search tab with real-time as-you-type query to Supabase (debounced ~400ms).
class SearchTab extends ConsumerStatefulWidget {
  const SearchTab({super.key});

  @override
  ConsumerState<SearchTab> createState() => _SearchTabState();
}

class _SearchTabState extends ConsumerState<SearchTab> {
  final _controller = TextEditingController();
  Timer? _debounce;
  String _query = '';
  bool _showResults = false;

  // Search results.
  List<Song> _songs = [];
  List<Artist> _artists = [];
  List<Album> _albums = [];
  List<Map<String, dynamic>> _playlists = [];
  bool _isLoading = false;

  @override
  void dispose() {
    _debounce?.cancel();
    _controller.dispose();
    super.dispose();
  }

  void _onSearchChanged(String value) {
    setState(() => _query = value);

    _debounce?.cancel();
    if (value.trim().isEmpty) {
      setState(() {
        _showResults = false;
        _songs = [];
        _artists = [];
        _albums = [];
        _playlists = [];
      });
      return;
    }

    _debounce = Timer(const Duration(milliseconds: 400), () {
      _performSearch(value.trim());
    });
  }

  Future<void> _performSearch(String query) async {
    if (query.isEmpty) return;

    setState(() => _isLoading = true);

    final repo = ref.read(catalogRepositoryProvider);
    try {
      final results = await Future.wait([
        repo.searchSongs(query),
        repo.searchArtists(query),
        repo.searchAlbums(query),
        repo.searchPlaylists(query),
      ]);

      if (!mounted) return;
      setState(() {
        _songs = results[0] as List<Song>;
        _artists = results[1] as List<Artist>;
        _albums = results[2] as List<Album>;
        _playlists = results[3] as List<Map<String, dynamic>>;
        _showResults = true;
        _isLoading = false;
      });
    } on Object {
      if (!mounted) return;
      setState(() => _isLoading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

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
              Padding(
                padding: const EdgeInsets.fromLTRB(20, 16, 20, 8),
                child: Text(
                  'Search',
                  style: Theme.of(context).textTheme.headlineLarge,
                ),
              ),
              GlassSearchBar(
                controller: _controller,
                onChanged: _onSearchChanged,
              ),
              const SizedBox(height: 8),
              Expanded(
                child: _buildContent(),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildContent() {
    if (_isLoading) {
      return const Center(
        child: CupertinoActivityIndicator(),
      );
    }

    if (_showResults) {
      return _buildResults();
    }

    if (_query.isEmpty) {
      return const _GenreGrid();
    }

    return const _SearchEmptyState();
  }

  Widget _buildResults() {
    final hasResults = _songs.isNotEmpty ||
        _artists.isNotEmpty ||
        _albums.isNotEmpty ||
        _playlists.isNotEmpty;

    if (!hasResults) {
      return const _SearchEmptyState();
    }

    return ListView(
      padding: const EdgeInsets.symmetric(horizontal: 20),
      children: [
        if (_songs.isNotEmpty) ...[
          _ResultSectionHeader(
            title: 'Songs',
            count: _songs.length,
          ),
          const SizedBox(height: 8),
          for (final song in _songs.take(5))
            Padding(
              padding: const EdgeInsets.only(bottom: 8),
              child: _SongResultTile(song: song),
            ),
          const SizedBox(height: 16),
        ],
        if (_artists.isNotEmpty) ...[
          _ResultSectionHeader(
            title: 'Artists',
            count: _artists.length,
          ),
          const SizedBox(height: 8),
          SizedBox(
            height: 100,
            child: ListView.separated(
              scrollDirection: Axis.horizontal,
              itemCount: _artists.length,
              separatorBuilder: (_, __) => const SizedBox(width: 12),
              itemBuilder: (context, index) =>
                  _ArtistResultCard(artist: _artists[index]),
            ),
          ),
          const SizedBox(height: 16),
        ],
        if (_albums.isNotEmpty) ...[
          _ResultSectionHeader(
            title: 'Albums',
            count: _albums.length,
          ),
          const SizedBox(height: 8),
          SizedBox(
            height: 180,
            child: ListView.separated(
              scrollDirection: Axis.horizontal,
              itemCount: _albums.length,
              separatorBuilder: (_, __) => const SizedBox(width: 12),
              itemBuilder: (context, index) =>
                  _AlbumResultCard(album: _albums[index]),
            ),
          ),
          const SizedBox(height: 16),
        ],
        if (_playlists.isNotEmpty) ...[
          _ResultSectionHeader(
            title: 'Playlists',
            count: _playlists.length,
          ),
          const SizedBox(height: 8),
          for (final pl in _playlists.take(5))
            Padding(
              padding: const EdgeInsets.only(bottom: 8),
              child: _PlaylistResultTile(playlist: pl),
            ),
          const SizedBox(height: 16),
        ],
      ],
    );
  }
}

// =============================================================================
// GENRE GRID (shown when search bar is empty)
// =============================================================================

class _GenreGrid extends StatelessWidget {
  const _GenreGrid();

  static const _genres = [
    'Pop',
    'Rock',
    'Hip-Hop',
    'Electronic',
    'Jazz',
    'K-Pop',
    'Indie',
    'Dangdut',
  ];

  @override
  Widget build(BuildContext context) {
    return GridView.builder(
      padding: const EdgeInsets.all(20),
      gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
        crossAxisCount: 2,
        mainAxisSpacing: 12,
        crossAxisSpacing: 12,
        childAspectRatio: 1.6,
      ),
      itemCount: _genres.length,
      itemBuilder: (context, index) {
        return GlassCard(
          padding: const EdgeInsets.all(16),
          child: Row(
            children: [
              const Icon(
                CupertinoIcons.music_note,
                color: AppColors.azureMistDeep,
              ),
              const SizedBox(width: 8),
              Text(
                _genres[index],
                style: Theme.of(context).textTheme.titleSmall,
              ),
            ],
          ),
        );
      },
    );
  }
}

// =============================================================================
// RESULT SECTION HEADER
// =============================================================================

class _ResultSectionHeader extends StatelessWidget {
  const _ResultSectionHeader({
    required this.title,
    required this.count,
  });

  final String title;
  final int count;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Text(
          title,
          style: Theme.of(context).textTheme.titleMedium?.copyWith(
                fontWeight: FontWeight.w600,
              ),
        ),
        const SizedBox(width: 8),
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
          decoration: BoxDecoration(
            color: AppColors.azureMistDeep.withOpacity(0.2),
            borderRadius: BorderRadius.circular(12),
          ),
          child: Text(
            '$count',
            style: const TextStyle(
              fontSize: 12,
              color: AppColors.azureMistDeep,
              fontWeight: FontWeight.w600,
            ),
          ),
        ),
      ],
    );
  }
}

// =============================================================================
// RESULT TILES
// =============================================================================

class _SongResultTile extends StatelessWidget {
  const _SongResultTile({required this.song});

  final Song song;

  @override
  Widget build(BuildContext context) {
    return GlassCard(
      borderRadius: 12,
      padding: const EdgeInsets.all(10),
      child: Row(
        children: [
          ClipRRect(
            borderRadius: BorderRadius.circular(8),
            child: SignedImage(
              value: song.coverUrl,
              width: 44,
              height: 44,
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
          const Icon(
            CupertinoIcons.chevron_right,
            size: 16,
            color: AppColors.textSecondary,
          ),
        ],
      ),
    );
  }
}

class _ArtistResultCard extends StatelessWidget {
  const _ArtistResultCard({required this.artist});

  final Artist artist;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: 80,
      child: Column(
        children: [
          ClipOval(
            child: SignedImage(
              value: artist.imageUrl,
              width: 64,
              height: 64,
              fallbackIcon: CupertinoIcons.person_fill,
            ),
          ),
          const SizedBox(height: 6),
          Text(
            artist.name,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            textAlign: TextAlign.center,
            style: Theme.of(context).textTheme.bodySmall,
          ),
        ],
      ),
    );
  }
}

class _AlbumResultCard extends StatelessWidget {
  const _AlbumResultCard({required this.album});

  final Album album;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: 120,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          ClipRRect(
            borderRadius: BorderRadius.circular(10),
            child: SignedImage(
              value: album.coverUrl,
              width: 120,
              height: 120,
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
          if (album.artistName != null)
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

class _PlaylistResultTile extends StatelessWidget {
  const _PlaylistResultTile({required this.playlist});

  final Map<String, dynamic> playlist;

  @override
  Widget build(BuildContext context) {
    return GlassCard(
      borderRadius: 12,
      padding: const EdgeInsets.all(10),
      child: Row(
        children: [
          ClipRRect(
            borderRadius: BorderRadius.circular(8),
            child: SignedImage(
              value: playlist['cover_url'] as String?,
              width: 44,
              height: 44,
              fallbackIcon: CupertinoIcons.music_note_list,
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  playlist['name'] as String? ?? '',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.w600,
                  ),
                ),
                const SizedBox(height: 2),
                const Text(
                  'Public Playlist',
                  style: TextStyle(
                    fontSize: 12,
                    color: AppColors.textSecondary,
                  ),
                ),
              ],
            ),
          ),
          const Icon(
            CupertinoIcons.chevron_right,
            size: 16,
            color: AppColors.textSecondary,
          ),
        ],
      ),
    );
  }
}

// =============================================================================
// EMPTY STATE
// =============================================================================

class _SearchEmptyState extends StatelessWidget {
  const _SearchEmptyState();

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          const Icon(
            CupertinoIcons.search,
            size: 48,
            color: AppColors.textSecondary,
          ),
          const SizedBox(height: 12),
          Text(
            'Tidak ada hasil ditemukan',
            style: Theme.of(context).textTheme.bodyMedium,
          ),
        ],
      ),
    );
  }
}
