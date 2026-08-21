import 'package:supabase_flutter/supabase_flutter.dart';

import '../../catalog/data/catalog_exception.dart';
import '../../catalog/models/song.dart';
import '../models/playlist.dart';

/// Repository untuk CRUD playlist & playlist_songs.
/// RLS: user hanya bisa CRUD miliknya sendiri (user_id = auth.uid()).
/// Playlist public bisa dibaca semua authenticated user (read-only).
class PlaylistRepository {
  PlaylistRepository(this._supabase);

  final SupabaseClient _supabase;

  String? get _uid => _supabase.auth.currentUser?.id;

  // ---------------------------------------------------------------------------
  // PLAYLISTS
  // ---------------------------------------------------------------------------

  Future<List<Playlist>> getMyPlaylists() async {
    try {
      final uid = _uid;
      if (uid == null) return [];
      final rows = await _supabase
          .from('playlists')
          .select('*, playlist_songs(count)')
          .eq('user_id', uid)
          .order('created_at', ascending: false);
      return rows.map(_mapPlaylistRow).toList();
    } catch (e) {
      throw CatalogException.from(e);
    }
  }

  Future<List<Playlist>> getPublicPlaylists(
      {int limit = 20, String? search,}) async {
    try {
      var query = _supabase
          .from('playlists')
          .select('*, playlist_songs(count)')
          .eq('is_public', true);
      final kw = search?.trim();
      if (kw != null && kw.isNotEmpty) {
        query = query.ilike('name', '%$kw%');
      }
      final rows =
          await query.order('created_at', ascending: false).limit(limit);
      return rows.map(_mapPlaylistRow).toList();
    } catch (e) {
      throw CatalogException.from(e);
    }
  }

  Future<Playlist?> getPlaylist(String id) async {
    try {
      final row = await _supabase
          .from('playlists')
          .select('*, playlist_songs(count)')
          .eq('id', id)
          .maybeSingle();
      if (row == null) return null;
      return _mapPlaylist(row);
    } catch (e) {
      throw CatalogException.from(e);
    }
  }

  Future<Playlist> createPlaylist({
    required String name,
    String? description,
    String? coverUrl,
    bool isPublic = false,
  }) async {
    try {
      final uid = _uid;
      if (uid == null) {
        throw const CatalogException('Harus login untuk membuat playlist.');
      }
      final row = await _supabase
          .from('playlists')
          .insert({
            'user_id': uid,
            'name': name.trim(),
            if (description != null && description.trim().isNotEmpty)
              'description': description.trim(),
            if (coverUrl != null && coverUrl.trim().isNotEmpty)
              'cover_url': coverUrl.trim(),
            'is_public': isPublic,
          })
          .select()
          .single();
      return Playlist.fromJson(row);
    } catch (e) {
      if (e is CatalogException) rethrow;
      throw CatalogException.from(e);
    }
  }

  Future<Playlist> updatePlaylist(
    String id, {
    String? name,
    String? description,
    String? coverUrl,
    bool? isPublic,
  }) async {
    try {
      final updates = <String, dynamic>{};
      if (name != null) updates['name'] = name.trim();
      if (description != null) {
        updates['description'] =
            description.trim().isEmpty ? null : description.trim();
      }
      if (coverUrl != null) {
        updates['cover_url'] = coverUrl.trim().isEmpty ? null : coverUrl.trim();
      }
      if (isPublic != null) updates['is_public'] = isPublic;
      if (updates.isEmpty) {
        final existing = await getPlaylist(id);
        if (existing == null) {
          throw const CatalogException('Playlist tidak ditemukan.');
        }
        return existing;
      }
      final row = await _supabase
          .from('playlists')
          .update(updates)
          .eq('id', id)
          .select()
          .single();
      return Playlist.fromJson(row);
    } catch (e) {
      if (e is CatalogException) rethrow;
      throw CatalogException.from(e);
    }
  }

  Future<void> deletePlaylist(String id) async {
    try {
      await _supabase.from('playlists').delete().eq('id', id);
    } catch (e) {
      throw CatalogException.from(e);
    }
  }

  // ---------------------------------------------------------------------------
  // PLAYLIST_SONGS
  // ---------------------------------------------------------------------------

  Future<List<Song>> getPlaylistSongs(String playlistId) async {
    try {
      final rows = await _supabase
          .from('playlist_songs')
          .select(
              'position, song:songs(*, album:albums(title), artist:artists(name))',)
          .eq('playlist_id', playlistId)
          .order('position');
      final songs = <Song>[];
      for (final r in rows) {
        final s = r['song'];
        if (s is Map<String, dynamic>) {
          songs.add(Song.fromJson(_mapSongRow(s)));
        }
      }
      return songs;
    } catch (e) {
      throw CatalogException.from(e);
    }
  }

  Future<void> addSongToPlaylist(String playlistId, String songId) async {
    try {
      // Determine next position.
      final existing = await _supabase
          .from('playlist_songs')
          .select('position')
          .eq('playlist_id', playlistId)
          .order('position', ascending: false)
          .limit(1);
      final nextPos =
          existing.isEmpty ? 0 : ((existing.first['position'] as int) + 1);
      await _supabase.from('playlist_songs').insert({
        'playlist_id': playlistId,
        'song_id': songId,
        'position': nextPos,
      });
    } catch (e) {
      throw CatalogException.from(e);
    }
  }

  Future<void> removeSongFromPlaylist(String playlistId, String songId) async {
    try {
      await _supabase
          .from('playlist_songs')
          .delete()
          .eq('playlist_id', playlistId)
          .eq('song_id', songId);
    } catch (e) {
      throw CatalogException.from(e);
    }
  }

  Future<bool> isSongInPlaylist(String playlistId, String songId) async {
    try {
      final rows = await _supabase
          .from('playlist_songs')
          .select('song_id')
          .eq('playlist_id', playlistId)
          .eq('song_id', songId)
          .limit(1);
      return rows.isNotEmpty;
    } catch (_) {
      return false;
    }
  }

  Future<void> reorderPlaylistSongs(
      String playlistId, List<String> orderedSongIds,) async {
    try {
      // Update position per song. Use upsert to ensure atomic per-row.
      for (var i = 0; i < orderedSongIds.length; i++) {
        await _supabase
            .from('playlist_songs')
            .update({'position': i})
            .eq('playlist_id', playlistId)
            .eq('song_id', orderedSongIds[i]);
      }
    } catch (e) {
      throw CatalogException.from(e);
    }
  }

  // ---------------------------------------------------------------------------
  // PRIVATE MAPPERS
  // ---------------------------------------------------------------------------

  Playlist _mapPlaylistRow(Map<String, dynamic> row) {
    return _mapPlaylist(Map<String, dynamic>.from(row));
  }

  Playlist _mapPlaylist(Map<String, dynamic> row) {
    final mapped = Map<String, dynamic>.from(row);
    final pc = row['playlist_songs'];
    if (pc is List && pc.isNotEmpty && pc.first is Map<String, dynamic>) {
      mapped['song_count'] = (pc.first as Map<String, dynamic>)['count'];
    } else if (pc is List) {
      mapped['song_count'] = pc.length;
    }
    mapped.remove('playlist_songs');
    return Playlist.fromJson(mapped);
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
