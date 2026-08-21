import 'package:supabase_flutter/supabase_flutter.dart';

import '../models/album.dart';
import '../models/artist.dart';
import '../models/song.dart';
import 'catalog_exception.dart';

/// Repository katalog: akses data artist/album/song via Supabase.
///
/// Semua method melempar [CatalogException] dengan pesan siap-tampil.
class CatalogRepository {
  CatalogRepository(this._supabase);

  final SupabaseClient _supabase;

  // ---------------------------------------------------------------------------
  // ARTISTS
  // ---------------------------------------------------------------------------

  Future<List<Artist>> getArtists({String? search}) async {
    try {
      var query = _supabase.from('artists').select();
      final keyword = search?.trim();
      if (keyword != null && keyword.isNotEmpty) {
        query = query.ilike('name', '%$keyword%');
      }
      final rows = await query.order('name');
      return rows.map(Artist.fromJson).toList();
    } catch (e) {
      throw CatalogException.from(e);
    }
  }

  Future<Artist?> getArtist(String id) async {
    try {
      final row =
          await _supabase.from('artists').select().eq('id', id).maybeSingle();
      return row == null ? null : Artist.fromJson(row);
    } catch (e) {
      throw CatalogException.from(e);
    }
  }

  Future<Artist> createArtist({
    required String name,
    String? bio,
    String? imageUrl,
  }) async {
    try {
      final row = await _supabase
          .from('artists')
          .insert({
            'name': name,
            if (bio != null && bio.isNotEmpty) 'bio': bio,
            if (imageUrl != null && imageUrl.isNotEmpty) 'image_url': imageUrl,
            'created_by': _supabase.auth.currentUser?.id,
          })
          .select()
          .single();
      return Artist.fromJson(row);
    } catch (e) {
      throw CatalogException.from(e);
    }
  }

  Future<void> updateArtist(
    String id, {
    String? name,
    String? bio,
    String? imageUrl,
    bool? isVerified,
  }) async {
    try {
      await _supabase.from('artists').update({
        if (name != null) 'name': name,
        if (bio != null) 'bio': bio,
        if (imageUrl != null) 'image_url': imageUrl,
        if (isVerified != null) 'is_verified': isVerified,
      }).eq('id', id);
    } catch (e) {
      throw CatalogException.from(e);
    }
  }

  Future<void> deleteArtist(String id) async {
    try {
      await _supabase.from('artists').delete().eq('id', id);
    } catch (e) {
      throw CatalogException.from(e);
    }
  }

  // ---------------------------------------------------------------------------
  // ALBUMS
  // ---------------------------------------------------------------------------

  Future<List<Album>> getAlbums({String? search}) async {
    try {
      var query = _supabase
          .from('albums')
          .select('*, artist:artists(name), songs(count)');
      final keyword = search?.trim();
      if (keyword != null && keyword.isNotEmpty) {
        query = query.ilike('title', '%$keyword%');
      }
      final rows = await query.order('title');
      return rows.map((row) => Album.fromJson(_mapAlbumRow(row))).toList();
    } catch (e) {
      throw CatalogException.from(e);
    }
  }

  Future<Album?> getAlbum(String id) async {
    try {
      final row = await _supabase
          .from('albums')
          .select('*, artist:artists(name), songs(count)')
          .eq('id', id)
          .maybeSingle();
      return row == null ? null : Album.fromJson(_mapAlbumRow(row));
    } catch (e) {
      throw CatalogException.from(e);
    }
  }

  Future<Album> createAlbum({
    required String title,
    required String artistId,
    String? coverUrl,
    DateTime? releaseDate,
    String? genre,
  }) async {
    try {
      final row = await _supabase
          .from('albums')
          .insert({
            'title': title,
            'artist_id': artistId,
            if (coverUrl != null && coverUrl.isNotEmpty) 'cover_url': coverUrl,
            if (releaseDate != null)
              'release_date': releaseDate.toIso8601String().split('T').first,
            if (genre != null && genre.isNotEmpty) 'genre': genre,
            'created_by': _supabase.auth.currentUser?.id,
          })
          .select()
          .single();
      return Album.fromJson(row);
    } catch (e) {
      throw CatalogException.from(e);
    }
  }

  Future<void> updateAlbum(
    String id, {
    String? title,
    String? artistId,
    String? coverUrl,
    DateTime? releaseDate,
    String? genre,
  }) async {
    try {
      await _supabase.from('albums').update({
        if (title != null) 'title': title,
        if (artistId != null) 'artist_id': artistId,
        if (coverUrl != null) 'cover_url': coverUrl,
        if (releaseDate != null)
          'release_date': releaseDate.toIso8601String().split('T').first,
        if (genre != null) 'genre': genre,
      }).eq('id', id);
    } catch (e) {
      throw CatalogException.from(e);
    }
  }

  Future<void> deleteAlbum(String id) async {
    try {
      await _supabase.from('albums').delete().eq('id', id);
    } catch (e) {
      throw CatalogException.from(e);
    }
  }

  // ---------------------------------------------------------------------------
  // SONGS
  // ---------------------------------------------------------------------------

  Future<List<Song>> getSongs({
    String? search,
    String? albumId,
    String? artistId,
    String? genre,
  }) async {
    try {
      var query = _supabase.from('songs').select(
            '*, album:albums(title), artist:artists(name)',
          );
      final keyword = search?.trim();
      if (keyword != null && keyword.isNotEmpty) {
        query = query.ilike('title', '%$keyword%');
      }
      if (albumId != null) {
        query = query.eq('album_id', albumId);
      }
      if (artistId != null) {
        query = query.eq('artist_id', artistId);
      }
      if (genre != null && genre.isNotEmpty) {
        query = query.ilike('genre', genre);
      }
      final rows = await query.order('album_id').order('track_number');
      return rows.map((row) => Song.fromJson(_mapSongRow(row))).toList();
    } catch (e) {
      throw CatalogException.from(e);
    }
  }

  /// Songs ordered by play_count descending (trending).
  Future<List<Song>> getTrendingSongs({int limit = 20}) async {
    try {
      final rows = await _supabase
          .from('songs')
          .select('*, album:albums(title), artist:artists(name)')
          .order('play_count', ascending: false)
          .limit(limit);
      return rows.map((row) => Song.fromJson(_mapSongRow(row))).toList();
    } catch (e) {
      throw CatalogException.from(e);
    }
  }

  /// Songs ordered by created_at descending (new releases).
  Future<List<Song>> getNewReleaseSongs({int limit = 20}) async {
    try {
      final rows = await _supabase
          .from('songs')
          .select('*, album:albums(title), artist:artists(name)')
          .order('created_at', ascending: false)
          .limit(limit);
      return rows.map((row) => Song.fromJson(_mapSongRow(row))).toList();
    } catch (e) {
      throw CatalogException.from(e);
    }
  }

  /// Albums ordered by created_at descending (new releases).
  Future<List<Album>> getNewReleaseAlbums({int limit = 10}) async {
    try {
      final rows = await _supabase
          .from('albums')
          .select('*, artist:artists(name), songs(count)')
          .order('created_at', ascending: false)
          .limit(limit);
      return rows.map((row) => Album.fromJson(_mapAlbumRow(row))).toList();
    } catch (e) {
      throw CatalogException.from(e);
    }
  }

  Future<Song?> getSong(String id) async {
    try {
      final row = await _supabase
          .from('songs')
          .select('*, album:albums(title), artist:artists(name)')
          .eq('id', id)
          .maybeSingle();
      return row == null ? null : Song.fromJson(_mapSongRow(row));
    } catch (e) {
      throw CatalogException.from(e);
    }
  }

  Future<Song> createSong({
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
    try {
      final row = await _supabase
          .from('songs')
          .insert({
            'title': title,
            'album_id': albumId,
            'artist_id': artistId,
            if (durationSeconds != null) 'duration_seconds': durationSeconds,
            if (audioUrl != null && audioUrl.isNotEmpty) 'audio_url': audioUrl,
            if (coverUrl != null && coverUrl.isNotEmpty) 'cover_url': coverUrl,
            if (genre != null && genre.isNotEmpty) 'genre': genre,
            if (lyrics != null && lyrics.isNotEmpty) 'lyrics': lyrics,
            'track_number': trackNumber,
            'uploaded_by': _supabase.auth.currentUser?.id,
          })
          .select()
          .single();
      return Song.fromJson(row);
    } catch (e) {
      throw CatalogException.from(e);
    }
  }

  Future<void> updateSong(
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
    try {
      await _supabase.from('songs').update({
        if (title != null) 'title': title,
        if (albumId != null) 'album_id': albumId,
        if (artistId != null) 'artist_id': artistId,
        if (durationSeconds != null) 'duration_seconds': durationSeconds,
        if (audioUrl != null) 'audio_url': audioUrl,
        if (coverUrl != null) 'cover_url': coverUrl,
        if (genre != null) 'genre': genre,
        if (lyrics != null) 'lyrics': lyrics,
        if (trackNumber != null) 'track_number': trackNumber,
      }).eq('id', id);
    } catch (e) {
      throw CatalogException.from(e);
    }
  }

  Future<void> deleteSong(String id) async {
    try {
      await _supabase.from('songs').delete().eq('id', id);
    } catch (e) {
      throw CatalogException.from(e);
    }
  }

  /// Reorder track dalam satu album (batch update track_number).
  Future<void> reorderAlbumTracks(
    String albumId,
    List<({String id, int trackNumber})> tracks,
  ) async {
    try {
      await _supabase.from('songs').upsert(
            tracks
                .map(
                  (t) => {
                    'id': t.id,
                    'album_id': albumId,
                    'track_number': t.trackNumber,
                  },
                )
                .toList(),
          );
    } catch (e) {
      throw CatalogException.from(e);
    }
  }

  // ---------------------------------------------------------------------------
  // SEARCH — multi-table
  // ---------------------------------------------------------------------------

  /// Search songs by title.
  Future<List<Song>> searchSongs(String query, {int limit = 20}) async {
    try {
      final keyword = query.trim();
      if (keyword.isEmpty) return const [];
      final rows = await _supabase
          .from('songs')
          .select('*, album:albums(title), artist:artists(name)')
          .ilike('title', '%$keyword%')
          .order('play_count', ascending: false)
          .limit(limit);
      return rows.map((row) => Song.fromJson(_mapSongRow(row))).toList();
    } catch (e) {
      throw CatalogException.from(e);
    }
  }

  /// Search artists by name.
  Future<List<Artist>> searchArtists(String query, {int limit = 20}) async {
    try {
      final keyword = query.trim();
      if (keyword.isEmpty) return const [];
      final rows = await _supabase
          .from('artists')
          .select()
          .ilike('name', '%$keyword%')
          .order('name')
          .limit(limit);
      return rows.map(Artist.fromJson).toList();
    } catch (e) {
      throw CatalogException.from(e);
    }
  }

  /// Search albums by title.
  Future<List<Album>> searchAlbums(String query, {int limit = 20}) async {
    try {
      final keyword = query.trim();
      if (keyword.isEmpty) return const [];
      final rows = await _supabase
          .from('albums')
          .select('*, artist:artists(name), songs(count)')
          .ilike('title', '%$keyword%')
          .order('title')
          .limit(limit);
      return rows.map((row) => Album.fromJson(_mapAlbumRow(row))).toList();
    } catch (e) {
      throw CatalogException.from(e);
    }
  }

  /// Search public playlists by name. Private playlists are excluded.
  Future<List<Map<String, dynamic>>> searchPlaylists(
    String query, {
    int limit = 20,
  }) async {
    try {
      final keyword = query.trim();
      if (keyword.isEmpty) return const [];
      final rows = await _supabase
          .from('playlists')
          .select('id, name, cover_url, user_id, is_public')
          .ilike('name', '%$keyword%')
          .eq('is_public', true)
          .order('name')
          .limit(limit);
      return rows;
    } catch (e) {
      throw CatalogException.from(e);
    }
  }

  // ---------------------------------------------------------------------------
  // ROLE
  // ---------------------------------------------------------------------------

  /// Role aplikasi user saat ini dari tabel `users`.
  Future<String?> currentUserRole() async {
    final userId = _supabase.auth.currentUser?.id;
    if (userId == null) {
      return null;
    }
    try {
      final row = await _supabase
          .from('users')
          .select('role')
          .eq('id', userId)
          .maybeSingle();
      return row == null ? null : row['role'] as String?;
    } catch (e) {
      throw CatalogException.from(e);
    }
  }

  // ---------------------------------------------------------------------------
  // PRIVATE
  // ---------------------------------------------------------------------------

  Map<String, dynamic> _mapAlbumRow(Map<String, dynamic> row) {
    final mapped = Map<String, dynamic>.from(row);
    final artist = row['artist'];
    if (artist is Map<String, dynamic>) {
      mapped['artist_name'] = artist['name'];
    }
    final songs = row['songs'];
    if (songs is List &&
        songs.isNotEmpty &&
        songs.first is Map<String, dynamic>) {
      mapped['song_count'] = (songs.first as Map<String, dynamic>)['count'];
    }
    mapped.remove('artist');
    mapped.remove('songs');
    return mapped;
  }

  Map<String, dynamic> _mapSongRow(Map<String, dynamic> row) {
    final mapped = Map<String, dynamic>.from(row);
    final album = row['album'];
    if (album is Map<String, dynamic>) {
      mapped['album_title'] = album['title'];
    }
    final artist = row['artist'];
    if (artist is Map<String, dynamic>) {
      mapped['artist_name'] = artist['name'];
    }
    mapped.remove('album');
    mapped.remove('artist');
    return mapped;
  }
}
