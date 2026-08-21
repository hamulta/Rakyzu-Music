import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:just_audio/just_audio.dart';

import '../../features/player/providers/player_provider.dart';

export '../../features/player/providers/player_controller.dart'
    show PlaybackState, RepeatMode;
export '../../features/player/providers/player_provider.dart'
    show currentTrackProvider, hasActiveTrackProvider, playerControllerProvider;

/// Instance `AudioPlayer` global — sekarang diinisialisasi dari PlayerController.
/// Dibiarkan backward-compatible untuk widget lama yang masih langsung
/// mengakses `ref.watch(audioPlayerProvider)`.
final audioPlayerProvider = Provider<AudioPlayer>((ref) {
  final controller = ref.watch(playerControllerProvider.notifier);
  return controller.player;
});

/// Kunci object audio yang sedang dimuat ke pemutar.
/// Sekarang di-sync dengan PlayerState.currentTrack.audioUrl.
final activeAudioKeyProvider = StateProvider<String?>((ref) => null);

/// Memuat `sourceUrl` ke pemutar global dan mulai memutar.
/// Method backward-compatible — di production, gunakan
/// `playerControllerProvider.notifier.playSingle(song)` langsung.
Future<bool> playAudioSource(WidgetRef ref, String url) async {
  try {
    await ref.read(audioPlayerProvider).setUrl(url);
    await ref.read(audioPlayerProvider).play();
    return true;
  } on PlayerException {
    return false;
  }
}

/// Berhenti & kosongkan pemutar.
Future<void> stopAudio(WidgetRef ref) async {
  await ref.read(audioPlayerProvider).stop();
  ref.read(activeAudioKeyProvider.notifier).state = null;
}
