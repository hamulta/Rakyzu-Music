import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../shared/providers/supabase_providers.dart';
import '../data/catalog_repository.dart';
import '../models/album.dart';
import '../models/artist.dart';
import '../models/genre.dart';
import '../models/song.dart';

/// Singleton repository katalog.
final catalogRepositoryProvider = Provider<CatalogRepository>((ref) {
  return CatalogRepository(ref.watch(supabaseProvider));
});

// ---------------------------------------------------------------------------
// ARTISTS
// ---------------------------------------------------------------------------

class ArtistsController extends StateNotifier<AsyncValue<List<Artist>>> {
  ArtistsController(this._repository) : super(const AsyncValue.loading()) {
    load();
  }

  final CatalogRepository _repository;
  String? _search;

  Future<void> load({String? search}) async {
    _search = search;
    state = const AsyncValue.loading();
    try {
      final artists = await _repository.getArtists(search: _search);
      state = AsyncValue.data(artists);
    } on Object catch (e, stack) {
      state = AsyncValue.error(e, stack);
    }
  }

  Future<void> refresh() => load(search: _search);

  Future<void> create({
    required String name,
    String? bio,
    String? imageUrl,
  }) async {
    await _repository.createArtist(name: name, bio: bio, imageUrl: imageUrl);
    await refresh();
  }

  Future<void> update(
    String id, {
    String? name,
    String? bio,
    String? imageUrl,
    bool? isVerified,
  }) async {
    await _repository.updateArtist(
      id,
      name: name,
      bio: bio,
      imageUrl: imageUrl,
      isVerified: isVerified,
    );
    await refresh();
  }

  Future<void> remove(String id) async {
    await _repository.deleteArtist(id);
    await refresh();
  }
}

final artistsControllerProvider =
    StateNotifierProvider<ArtistsController, AsyncValue<List<Artist>>>((ref) {
  return ArtistsController(ref.watch(catalogRepositoryProvider));
});

// ---------------------------------------------------------------------------
// ALBUMS
// ---------------------------------------------------------------------------

class AlbumsController extends StateNotifier<AsyncValue<List<Album>>> {
  AlbumsController(this._repository) : super(const AsyncValue.loading()) {
    load();
  }

  final CatalogRepository _repository;
  String? _search;

  Future<void> load({String? search}) async {
    _search = search;
    state = const AsyncValue.loading();
    try {
      final albums = await _repository.getAlbums(search: _search);
      state = AsyncValue.data(albums);
    } on Object catch (e, stack) {
      state = AsyncValue.error(e, stack);
    }
  }

  Future<void> refresh() => load(search: _search);

  Future<void> create({
    required String title,
    required String artistId,
    String? coverUrl,
    DateTime? releaseDate,
    String? genre,
  }) async {
    await _repository.createAlbum(
      title: title,
      artistId: artistId,
      coverUrl: coverUrl,
      releaseDate: releaseDate,
      genre: genre,
    );
    await refresh();
  }

  Future<void> update(
    String id, {
    String? title,
    String? artistId,
    String? coverUrl,
    DateTime? releaseDate,
    String? genre,
  }) async {
    await _repository.updateAlbum(
      id,
      title: title,
      artistId: artistId,
      coverUrl: coverUrl,
      releaseDate: releaseDate,
      genre: genre,
    );
    await refresh();
  }

  Future<void> remove(String id) async {
    await _repository.deleteAlbum(id);
    await refresh();
  }
}

final albumsControllerProvider =
    StateNotifierProvider<AlbumsController, AsyncValue<List<Album>>>((ref) {
  return AlbumsController(ref.watch(catalogRepositoryProvider));
});

// ---------------------------------------------------------------------------
// SONGS
// ---------------------------------------------------------------------------

class SongsController extends StateNotifier<AsyncValue<List<Song>>> {
  SongsController(this._repository) : super(const AsyncValue.loading()) {
    load();
  }

  final CatalogRepository _repository;
  String? _search;
  String? _albumId;
  String? _artistId;
  String? _genre;

  Future<void> load({
    String? search,
    String? albumId,
    String? artistId,
    String? genre,
  }) async {
    _search = search;
    _albumId = albumId;
    _artistId = artistId;
    _genre = genre;
    state = const AsyncValue.loading();
    try {
      final songs = await _repository.getSongs(
        search: _search,
        albumId: _albumId,
        artistId: _artistId,
        genre: _genre,
      );
      state = AsyncValue.data(songs);
    } on Object catch (e, stack) {
      state = AsyncValue.error(e, stack);
    }
  }

  Future<void> refresh() => load(
        search: _search,
        albumId: _albumId,
        artistId: _artistId,
        genre: _genre,
      );

  Future<void> create({
    required String title,
    required String albumId,
    required String artistId,
    int? durationSeconds,
    String? audioUrl,
    String? coverUrl,
    String? genre,
    String? lyrics,
    int trackNumber = 0,
  }) async {
    await _repository.createSong(
      title: title,
      albumId: albumId,
      artistId: artistId,
      durationSeconds: durationSeconds,
      audioUrl: audioUrl,
      coverUrl: coverUrl,
      genre: genre,
      lyrics: lyrics,
      trackNumber: trackNumber,
    );
    await refresh();
  }

  Future<void> update(
    String id, {
    String? title,
    String? albumId,
    String? artistId,
    int? durationSeconds,
    String? audioUrl,
    String? coverUrl,
    String? genre,
    String? lyrics,
    int? trackNumber,
  }) async {
    await _repository.updateSong(
      id,
      title: title,
      albumId: albumId,
      artistId: artistId,
      durationSeconds: durationSeconds,
      audioUrl: audioUrl,
      coverUrl: coverUrl,
      genre: genre,
      lyrics: lyrics,
      trackNumber: trackNumber,
    );
    await refresh();
  }

  Future<void> remove(String id) async {
    await _repository.deleteSong(id);
    await refresh();
  }
}

final songsControllerProvider =
    StateNotifierProvider<SongsController, AsyncValue<List<Song>>>((ref) {
  return SongsController(ref.watch(catalogRepositoryProvider));
});

// ---------------------------------------------------------------------------
// TRENDING SONGS
// ---------------------------------------------------------------------------

final trendingSongsProvider = FutureProvider<List<Song>>((ref) async {
  final repo = ref.watch(catalogRepositoryProvider);
  return repo.getTrendingSongs(limit: 20);
});

// ---------------------------------------------------------------------------
// NEW RELEASE SONGS
// ---------------------------------------------------------------------------

final newReleaseSongsProvider = FutureProvider<List<Song>>((ref) async {
  final repo = ref.watch(catalogRepositoryProvider);
  return repo.getNewReleaseSongs(limit: 20);
});

// ---------------------------------------------------------------------------
// NEW RELEASE ALBUMS
// ---------------------------------------------------------------------------

final newReleaseAlbumsProvider = FutureProvider<List<Album>>((ref) async {
  final repo = ref.watch(catalogRepositoryProvider);
  return repo.getNewReleaseAlbums(limit: 10);
});

// ---------------------------------------------------------------------------
// GENRES
// ---------------------------------------------------------------------------

final genresProvider = FutureProvider<List<Genre>>((ref) async {
  final repo = ref.watch(catalogRepositoryProvider);
  return repo.getGenres();
});

/// Songs by genre — takes genre name as argument.
final songsByGenreProvider =
    FutureProvider.family<List<Song>, String>((ref, genre) async {
  final repo = ref.watch(catalogRepositoryProvider);
  return repo.getSongsByGenre(genre);
});

// ---------------------------------------------------------------------------
// ARTIST DETAIL
// ---------------------------------------------------------------------------

/// Follower count for an artist — takes artistId as argument.
final artistFollowerCountProvider =
    FutureProvider.family<int, String>((ref, artistId) async {
  final repo = ref.watch(catalogRepositoryProvider);
  return repo.getArtistFollowerCount(artistId);
});

/// Whether current user follows an artist — takes artistId as argument.
final isFollowingArtistProvider =
    FutureProvider.family<bool, String>((ref, artistId) async {
  final repo = ref.watch(catalogRepositoryProvider);
  return repo.isFollowingArtist(artistId);
});

/// Top tracks for an artist — takes artistId as argument.
final artistTopTracksProvider =
    FutureProvider.family<List<Song>, String>((ref, artistId) async {
  final repo = ref.watch(catalogRepositoryProvider);
  return repo.getArtistTopTracks(artistId);
});

/// Albums by an artist — takes artistId as argument.
final artistAlbumsProvider =
    FutureProvider.family<List<Album>, String>((ref, artistId) async {
  final repo = ref.watch(catalogRepositoryProvider);
  return repo.getArtistAlbums(artistId);
});
