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
import '../../../search/data/search_history_service.dart';

/// Search history service provider (singleton).
final searchHistoryServiceProvider = Provider<SearchHistoryService>((ref) {
  return SearchHistoryService();
});

/// Search history state provider.
final searchHistoryProvider =
    FutureProvider.autoDispose<List<String>>((ref) async {
  final service = ref.watch(searchHistoryServiceProvider);
  return service.getHistory();
});

/// Search tab with real-time as-you-type query to Supabase (debounced ~400ms).
/// Results are displayed in tabs: Songs, Artists, Albums, Playlists.
/// Search history is shown as suggestion chips when the bar is empty.
class SearchTab extends ConsumerStatefulWidget {
  const SearchTab({super.key});

  @override
  ConsumerState<SearchTab> createState() => _SearchTabState();
}

class _SearchTabState extends ConsumerState<SearchTab>
    with SingleTickerProviderStateMixin {
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

  late final TabController _tabController;

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 4, vsync: this);
  }

  @override
  void dispose() {
    _debounce?.cancel();
    _controller.dispose();
    _tabController.dispose();
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

    // Save to history.
    final historyService = ref.read(searchHistoryServiceProvider);
    await historyService.addQuery(query);
    ref.invalidate(searchHistoryProvider);

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

  void _onHistoryTap(String query) {
    _controller.text = query;
    _onSearchChanged(query);
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
              if (_showResults && !_isLoading) _buildTabs(),
              Expanded(
                child: _buildContent(),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildTabs() {
    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 20),
      decoration: BoxDecoration(
        color: AppColors.textSecondary.withOpacity(0.1),
        borderRadius: BorderRadius.circular(12),
      ),
      child: TabBar(
        controller: _tabController,
        isScrollable: true,
        labelColor: AppColors.azureMistDeep,
        unselectedLabelColor: AppColors.textSecondary,
        indicatorColor: AppColors.azureMistDeep,
        indicatorSize: TabBarIndicatorSize.label,
        tabs: [
          _TabWithBadge(label: 'Songs', count: _songs.length),
          _TabWithBadge(label: 'Artists', count: _artists.length),
          _TabWithBadge(label: 'Albums', count: _albums.length),
          _TabWithBadge(label: 'Playlists', count: _playlists.length),
        ],
      ),
    );
  }

  Widget _buildContent() {
    if (_isLoading) {
      return const Center(child: CupertinoActivityIndicator());
    }

    if (_showResults) {
      return TabBarView(
        controller: _tabController,
        children: [
          _SongsTab(songs: _songs),
          _ArtistsTab(artists: _artists),
          _AlbumsTab(albums: _albums),
          _PlaylistsTab(playlists: _playlists),
        ],
      );
    }

    if (_query.isEmpty) {
      return const _SearchIdleView();
    }

    return const _SearchEmptyState();
  }
}

// =============================================================================
// SEARCH IDLE VIEW (genre grid + history chips)
// =============================================================================

class _SearchIdleView extends ConsumerWidget {
  const _SearchIdleView();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final historyAsync = ref.watch(searchHistoryProvider);

    return ListView(
      padding: const EdgeInsets.all(20),
      children: [
        // Search history chips.
        historyAsync.when(
          data: (history) {
            if (history.isEmpty) return const SizedBox.shrink();
            return Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    const Icon(CupertinoIcons.clock,
                        size: 16, color: AppColors.textSecondary),
                    const SizedBox(width: 6),
                    Text(
                      'Recent Searches',
                      style: Theme.of(context).textTheme.titleSmall?.copyWith(
                            color: AppColors.textSecondary,
                          ),
                    ),
                    const Spacer(),
                    GestureDetector(
                      onTap: () async {
                        final service = ref.read(searchHistoryServiceProvider);
                        await service.clearHistory();
                        ref.invalidate(searchHistoryProvider);
                      },
                      child: const Text(
                        'Clear',
                        style: TextStyle(
                          fontSize: 12,
                          color: AppColors.azureMistDeep,
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 8),
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: [
                    for (final query in history.take(8))
                      GestureDetector(
                        onTap: () {
                          // Find the SearchTabState and call _onHistoryTap.
                          final state = context
                              .findAncestorStateOfType<_SearchTabState>();
                          state?._onHistoryTap(query);
                        },
                        child: Container(
                          padding: const EdgeInsets.symmetric(
                              horizontal: 12, vertical: 6),
                          decoration: BoxDecoration(
                            color: AppColors.textSecondary.withOpacity(0.1),
                            borderRadius: BorderRadius.circular(16),
                            border: Border.all(
                              color: AppColors.textSecondary.withOpacity(0.2),
                            ),
                          ),
                          child: Text(
                            query,
                            style: const TextStyle(
                              fontSize: 13,
                              color: AppColors.textPrimary,
                            ),
                          ),
                        ),
                      ),
                  ],
                ),
                const SizedBox(height: 20),
              ],
            );
          },
          loading: () => const SizedBox.shrink(),
          error: (_, __) => const SizedBox.shrink(),
        ),

        // Genre grid.
        Text(
          'Browse Genres',
          style: Theme.of(context).textTheme.titleLarge,
        ),
        const SizedBox(height: 12),
        const _GenreGrid(),
      ],
    );
  }
}

// =============================================================================
// TAB WITH BADGE
// =============================================================================

class _TabWithBadge extends StatelessWidget {
  const _TabWithBadge({required this.label, required this.count});

  final String label;
  final int count;

  @override
  Widget build(BuildContext context) {
    return Tab(
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(label),
          if (count > 0) ...[
            const SizedBox(width: 6),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 1),
              decoration: BoxDecoration(
                color: AppColors.azureMistDeep.withOpacity(0.2),
                borderRadius: BorderRadius.circular(8),
              ),
              child: Text(
                '$count',
                style: const TextStyle(
                  fontSize: 10,
                  color: AppColors.azureMistDeep,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }
}

// =============================================================================
// TAB VIEWS
// =============================================================================

class _SongsTab extends StatelessWidget {
  const _SongsTab({required this.songs});

  final List<Song> songs;

  @override
  Widget build(BuildContext context) {
    if (songs.isEmpty) return const _TabEmptyState(message: 'No songs found');

    return ListView.builder(
      padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
      itemCount: songs.length,
      itemBuilder: (context, index) => _SongResultTile(song: songs[index]),
    );
  }
}

class _ArtistsTab extends StatelessWidget {
  const _ArtistsTab({required this.artists});

  final List<Artist> artists;

  @override
  Widget build(BuildContext context) {
    if (artists.isEmpty) {
      return const _TabEmptyState(message: 'No artists found');
    }

    return GridView.builder(
      padding: const EdgeInsets.all(20),
      gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
        crossAxisCount: 3,
        mainAxisSpacing: 16,
        crossAxisSpacing: 12,
        childAspectRatio: 0.8,
      ),
      itemCount: artists.length,
      itemBuilder: (context, index) =>
          _ArtistResultCard(artist: artists[index]),
    );
  }
}

class _AlbumsTab extends StatelessWidget {
  const _AlbumsTab({required this.albums});

  final List<Album> albums;

  @override
  Widget build(BuildContext context) {
    if (albums.isEmpty) {
      return const _TabEmptyState(message: 'No albums found');
    }

    return GridView.builder(
      padding: const EdgeInsets.all(20),
      gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
        crossAxisCount: 2,
        mainAxisSpacing: 12,
        crossAxisSpacing: 12,
        childAspectRatio: 0.85,
      ),
      itemCount: albums.length,
      itemBuilder: (context, index) => _AlbumResultCard(album: albums[index]),
    );
  }
}

class _PlaylistsTab extends StatelessWidget {
  const _PlaylistsTab({required this.playlists});

  final List<Map<String, dynamic>> playlists;

  @override
  Widget build(BuildContext context) {
    if (playlists.isEmpty) {
      return const _TabEmptyState(message: 'No playlists found');
    }

    return ListView.builder(
      padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
      itemCount: playlists.length,
      itemBuilder: (context, index) =>
          _PlaylistResultTile(playlist: playlists[index]),
    );
  }
}

// =============================================================================
// GENRE GRID
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
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
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
    return Column(
      children: [
        ClipOval(
          child: SignedImage(
            value: artist.imageUrl,
            width: 80,
            height: 80,
            fallbackIcon: CupertinoIcons.person_fill,
          ),
        ),
        const SizedBox(height: 8),
        Text(
          artist.name,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          textAlign: TextAlign.center,
          style: Theme.of(context).textTheme.bodyMedium,
        ),
      ],
    );
  }
}

class _AlbumResultCard extends StatelessWidget {
  const _AlbumResultCard({required this.album});

  final Album album;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        ClipRRect(
          borderRadius: BorderRadius.circular(10),
          child: SignedImage(
            value: album.coverUrl,
            width: double.infinity,
            height: 120,
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
        if (album.artistName != null)
          Text(
            album.artistName!,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: Theme.of(context).textTheme.bodySmall,
          ),
      ],
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
// EMPTY STATES
// =============================================================================

class _TabEmptyState extends StatelessWidget {
  const _TabEmptyState({required this.message});

  final String message;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          const Icon(
            CupertinoIcons.search,
            size: 40,
            color: AppColors.textSecondary,
          ),
          const SizedBox(height: 12),
          Text(
            message,
            style: Theme.of(context).textTheme.bodyMedium,
          ),
        ],
      ),
    );
  }
}

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
