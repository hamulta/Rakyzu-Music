import 'package:flutter/foundation.dart';
import 'package:google_mobile_ads/google_mobile_ads.dart';

import 'ads_config.dart';

/// Service terpusat untuk Google Mobile Ads.
/// Menangani init, load banner & interstitial, impression logging hook.
class AdsService {
  AdsService._();

  static final AdsService instance = AdsService._();

  bool _initialized = false;
  InterstitialAd? _interstitial;
  bool _isLoadingInterstitial = false;

  /// Callback ketika interstitial selesai (untuk refresh).
  void Function()? onInterstitialDismissed;

  /// Callback untuk impression logging.
  void Function(String adType)? onAdImpression;

  Future<void> initialize() async {
    if (kIsWeb) return;
    if (_initialized) return;
    try {
      await MobileAds.instance.initialize();
      _initialized = true;
      // Preload interstitial pertama.
      await loadInterstitial();
    } on Object catch (e) {
      debugPrint('[AdsService] init failed: $e');
    }
  }

  bool get isInitialized => _initialized;

  // ---------------------------------------------------------------------------
  // Banner
  // ---------------------------------------------------------------------------

  BannerAd createBannerAd({
    required void Function(Ad) onAdLoaded,
    required void Function(Ad, LoadAdError) onAdFailed,
  }) {
    return BannerAd(
      adUnitId: AdsConfig.bannerAdUnitId,
      size: AdSize.banner,
      request: const AdRequest(),
      listener: BannerAdListener(
        onAdLoaded: (ad) {
          onAdImpression?.call('banner');
          onAdLoaded(ad);
        },
        onAdFailedToLoad: onAdFailed,
        onAdImpression: (_) => onAdImpression?.call('banner'),
      ),
    )..load();
  }

  // ---------------------------------------------------------------------------
  // Interstitial
  // ---------------------------------------------------------------------------

  Future<void> loadInterstitial() async {
    if (kIsWeb) return;
    if (_isLoadingInterstitial) return;
    if (!_initialized) return;
    _isLoadingInterstitial = true;
    try {
      await InterstitialAd.load(
        adUnitId: AdsConfig.interstitialAdUnitId,
        request: const AdRequest(),
        adLoadCallback: InterstitialAdLoadCallback(
          onAdLoaded: (ad) {
            _interstitial = ad;
            _isLoadingInterstitial = false;
            ad.fullScreenContentCallback = FullScreenContentCallback(
              onAdDismissedFullScreenContent: (ad) {
                ad.dispose();
                _interstitial = null;
                onInterstitialDismissed?.call();
                // Preload next.
                loadInterstitial();
              },
              onAdFailedToShowFullScreenContent: (ad, _) {
                ad.dispose();
                _interstitial = null;
                loadInterstitial();
              },
              onAdImpression: (_) => onAdImpression?.call('interstitial'),
            );
          },
          onAdFailedToLoad: (_) {
            _isLoadingInterstitial = false;
            // Retry after delay — jangan spam.
            Future<void>.delayed(const Duration(seconds: 30), loadInterstitial);
          },
        ),
      );
    } on Object {
      _isLoadingInterstitial = false;
    }
  }

  /// Tampilkan interstitial jika sudah ready. Return true jika tampil.
  Future<bool> showInterstitialIfReady() async {
    if (kIsWeb) return false;
    final ad = _interstitial;
    if (ad == null) {
      // Trigger load jika belum ada.
      await loadInterstitial();
      return false;
    }
    try {
      await ad.show();
      _interstitial = null;
      return true;
    } on Object {
      return false;
    }
  }

  void dispose() {
    _interstitial?.dispose();
    _interstitial = null;
  }
}
