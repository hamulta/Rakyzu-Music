import 'package:flutter/foundation.dart';
import 'package:startapp_sdk/startapp.dart';

// ads_config kept for N interval, not direct banner ID now (Start.io uses App ID only)

/// Service terpusat untuk Start.io ads (swap dari AdMob di 0.9.3).
/// App ID Start.io: 207228132 — test/sandbox mode aktif di develop.
/// Web tetap fallback ke placeholder, AdsGateProvider tidak perlu diubah.
class AdsService {
  AdsService._();
  static final AdsService instance = AdsService._();

  final StartAppSdk _sdk = StartAppSdk();
  bool _initialized = false;
  StartAppInterstitialAd? _interstitial;
  bool _isLoadingInterstitial = false;

  void Function()? onInterstitialDismissed;
  void Function(String adType)? onAdImpression;

  Future<void> initialize() async {
    if (kIsWeb) return;
    if (_initialized) return;
    try {
      // Start.io test mode — wajib di development, matikan sebelum production live (0.9.x final)
      await _sdk.setTestAdsEnabled(true);
      _initialized = true;
      await loadInterstitial();
    } catch (e) {
      debugPrint('[AdsService Start.io] init failed: $e');
    }
  }

  bool get isInitialized => _initialized;

  // Banner: caller uses StartAppBanner widget directly via AdsService helper.
  Future<StartAppBannerAd?> loadBannerAd() async {
    if (kIsWeb) return null;
    if (!_initialized) return null;
    try {
      final ad = await _sdk.loadBannerAd(StartAppBannerType.BANNER);
      onAdImpression?.call('banner');
      return ad;
    } catch (e) {
      debugPrint('[AdsService] banner load failed: $e');
      return null;
    }
  }

  // Legacy wrapper untuk AdBanner lama (google_mobile_ads) — kini pakai Start.io.
  // AdBanner widget akan memanggil loadBannerAd dan menampilkan StartAppBanner.
  // Keep method signature untuk minim rewrite UI.
  dynamic createBannerAd({required void Function(dynamic) onAdLoaded, required void Function(dynamic, dynamic) onAdFailed}) {
    // Start.io banner loading via async — panggil manual di widget.
    // Return dummy untuk kompatibilitas, widget sebenarnya pakai loadBannerAd().
    return null;
  }

  Future<void> loadInterstitial() async {
    if (kIsWeb) return;
    if (_isLoadingInterstitial) return;
    if (!_initialized) return;
    _isLoadingInterstitial = true;
    try {
      final ad = await _sdk.loadInterstitialAd();
      _interstitial = ad;
      _isLoadingInterstitial = false;
    } catch (_) {
      _isLoadingInterstitial = false;
      Future.delayed(const Duration(seconds: 30), loadInterstitial);
    }
  }

  Future<bool> showInterstitialIfReady() async {
    if (kIsWeb) return false;
    final ad = _interstitial;
    if (ad == null) {
      await loadInterstitial();
      return false;
    }
    try {
      await ad.show();
      onAdImpression?.call('interstitial');
      _interstitial = null;
      onInterstitialDismissed?.call();
      await loadInterstitial();
      return true;
    } catch (_) {
      return false;
    }
  }

  void dispose() {
    _interstitial = null;
  }
}
