import 'package:flutter/foundation.dart';

/// Test Ad Unit IDs resmi Google — JANGAN pakai production ID di develop.
/// Production ID baru di 0.9.x/1.0.0.
/// Sumber: https://developers.google.com/admob/android/test-ads
class AdsConfig {
  AdsConfig._();

  /// Banner test IDs.
  static String get bannerAdUnitId {
    if (kIsWeb) return '';
    // Android & iOS memakai test ID yang sama untuk banner (Google menyediakan 1).
    return 'ca-app-pub-3940256099942544/6300978111';
  }

  /// Interstitial test IDs.
  static String get interstitialAdUnitId {
    if (kIsWeb) return '';
    return 'ca-app-pub-3940256099942544/1033173712';
  }

  /// Android / iOS App IDs (sudah di Manifest/Info.plist).
  static const String androidAppId = 'ca-app-pub-3940256099942544~3347511713';
  static const String iosAppId = 'ca-app-pub-3940256099942544~1458002511';

  /// Interval interstitial: tiap N lagu diputar penuh untuk Free tier.
  /// N=3 dipilih karena balance: cukup sering untuk monetisasi, tidak terlalu intrusif.
  /// Spotify free menayangkan audio ad tiap ~3-4 lagu; angka 3 untuk MVP dapat di-tune via remote config nanti.
  static const int interstitialInterval = 3;

  /// Skip limit Free tier.
  static const int freeSkipLimitPerHour = 6;
}
