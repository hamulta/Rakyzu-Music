import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

/// Logger sederhana untuk impression ad.
/// Simpan lokal (SharedPreferences) + opsional insert ke Supabase tabel ringan.
/// Basis untuk Analytics Dashboard 0.8.x.
class AdImpressionLogger {
  AdImpressionLogger._();
  static final AdImpressionLogger instance = AdImpressionLogger._();

  static const _prefKeyBanner = 'ad_impressions_banner';
  static const _prefKeyInterstitial = 'ad_impressions_interstitial';

  int _bannerCount = 0;
  int _interstitialCount = 0;

  int get bannerCount => _bannerCount;
  int get interstitialCount => _interstitialCount;
  int get totalCount => _bannerCount + _interstitialCount;

  Future<void> init() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      _bannerCount = prefs.getInt(_prefKeyBanner) ?? 0;
      _interstitialCount = prefs.getInt(_prefKeyInterstitial) ?? 0;
    } on Object catch (e) {
      debugPrint('[AdLogger] init failed: $e');
    }
  }

  Future<void> log(String adType) async {
    if (adType == 'banner') {
      _bannerCount++;
    } else if (adType == 'interstitial') {
      _interstitialCount++;
    } else {
      return;
    }
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setInt(_prefKeyBanner, _bannerCount);
      await prefs.setInt(_prefKeyInterstitial, _interstitialCount);
    } on Object catch (e) {
      debugPrint('[AdLogger] save failed: $e');
    }

    // Opsional: insert ke Supabase jika ada tabel ad_impressions (best-effort, jangan gagalkan).
    try {
      final supa = Supabase.instance.client;
      final uid = supa.auth.currentUser?.id;
      await supa.from('ad_impressions').insert({
        'user_id': uid,
        'ad_type': adType,
        'created_at': DateTime.now().toIso8601String(),
      });
    } on Object {
      // Tabel belum ada di 0.6.x — silent, cukup lokal.
    }
    debugPrint('[AdLogger] $adType impression → total: $totalCount');
  }
}
