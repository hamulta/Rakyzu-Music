import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../shared/providers/supabase_providers.dart';
import '../../catalog/models/song.dart';
import '../data/playlist_repository.dart';
import '../models/playlist.dart';

final playlistRepositoryProvider = Provider<PlaylistRepository>((ref) {
  return PlaylistRepository(ref.watch(supabaseProvider));
});

final myPlaylistsProvider = FutureProvider<List<Playlist>>((ref) async {
  final repo = ref.watch(playlistRepositoryProvider);
  return repo.getMyPlaylists();
});

final playlistDetailProvider =
    FutureProvider.family<Playlist?, String>((ref, id) async {
  final repo = ref.watch(playlistRepositoryProvider);
  return repo.getPlaylist(id);
});

final playlistSongsProvider =
    FutureProvider.family<List<Song>, String>((ref, playlistId) async {
  final repo = ref.watch(playlistRepositoryProvider);
  return repo.getPlaylistSongs(playlistId);
});

// Mutation helper – invalidate after write.
Future<Playlist> createPlaylistAndRefresh(
  WidgetRef ref, {
  required String name,
  String? description,
  String? coverUrl,
  bool isPublic = false,
}) async {
  final repo = ref.read(playlistRepositoryProvider);
  final pl = await repo.createPlaylist(
    name: name,
    description: description,
    coverUrl: coverUrl,
    isPublic: isPublic,
  );
  ref.invalidate(myPlaylistsProvider);
  return pl;
}
