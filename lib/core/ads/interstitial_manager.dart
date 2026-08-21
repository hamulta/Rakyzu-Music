import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../features/player/providers/player_provider.dart';
import 'ads_gate_provider.dart';
import 'ads_service.dart';

/// Observer provider untuk interstitial tiap N=3 lagu full play pada Free tier.
/// Tidak mengganggu queue/playback — hanya show interstitial terpisah.
final interstitialManagerProvider = Provider<void>((ref) {
  if (kIsWeb) return;

  // Listen perubahan songsPlayedSinceAd.
  ref.listen(playerControllerProvider, (previous, next) {
    final gate = ref.read(adsGateProvider);
    if (!gate.shouldShowAds) return;

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
