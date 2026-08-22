import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../features/player/providers/player_provider.dart';
import 'ads_gate_provider.dart';
import 'ads_service.dart';

/// Observer terpusat untuk interstitial — HANYA Free, interval tetap N=5.
/// Dipicu oleh counter songsPlayedSinceAd (increment tiap lagu selesai penuh).
/// Tidak ada random/timer — tepat setiap 5 lagu, reset ke 0 setelah tampil.
final interstitialManagerProvider = Provider<void>((ref) {
  if (kIsWeb) return;

  ref.listen(playerControllerProvider, (previous, next) {
    final gate = ref.read(adsGateProvider);
    if (!gate.shouldShowAds) {
      // Premium/Staff/Admin/Owner: jangan panggil showAd sama sekali
      return;
    }

    final controller = ref.read(playerControllerProvider.notifier);
    if (!controller.shouldShowInterstitial) return;

    // Jangan show jika sedang tidak ada track atau loading.
    if (next.currentTrack == null) return;

    // Show interstitial, reset counter setelah tampil (atau attempt).
    // ignore: discarded_futures
    AdsService.instance.showInterstitialIfReady().then((shown) {
      if (shown) controller.resetInterstitialCounter();
      // Jika gagal load, biarkan counter, akan retry di lagu berikutnya tanpa reset.
      // Tidak mengganggu playback.
    });
  });
});
