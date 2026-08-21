import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../catalog/data/r2_storage_service.dart';
import '../../catalog/models/song.dart';
import 'player_controller.dart';

/// Global player controller — single source of truth untuk seluruh app.
///
/// Menyediakan akses ke `PlaybackState` via `ref.watch(playerControllerProvider)`
/// dan aksi via `ref.read(playerControllerProvider.notifier)`.
final playerControllerProvider =
    StateNotifierProvider<PlayerController, PlaybackState>((ref) {
  final r2 = ref.watch(r2StorageServiceProvider);
  return PlayerController(r2);
});

/// Convenience: apakah ada lagu yang sedang dimutar.
final hasActiveTrackProvider = Provider<bool>((ref) {
  return ref.watch(playerControllerProvider).hasTrack;
});

/// Convenience: current track.
final currentTrackProvider = Provider<Song?>((ref) {
  return ref.watch(playerControllerProvider).currentTrack;
});
