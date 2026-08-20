import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:just_audio/just_audio.dart';

/// Instance `AudioPlayer` global (satu pemutar untuk seluruh app).
/// Dispose otomatis saat provider dihancurkan.
final audioPlayerProvider = Provider<AudioPlayer>((ref) {
  final player = AudioPlayer();
  ref.onDispose(player.dispose);
  return player;
});

/// Kunci object audio yang sedang dimuat ke `audioPlayerProvider`.
/// Null berarti tidak ada audio aktif.
final activeAudioKeyProvider = StateProvider<String?>((ref) => null);

/// Memuat `sourceUrl` ke pemutar global dan mulai memutar.
/// Mengembalikan `true` bila berhasil, `false` bila gagal.
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
