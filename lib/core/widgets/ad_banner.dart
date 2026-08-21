import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_mobile_ads/google_mobile_ads.dart';

import '../ads/ads_gate_provider.dart';
import '../ads/ads_service.dart';
import 'web_ad_slot.dart';

/// Banner yang otomatis:
/// - kIsWeb  -> WebAdSlot placeholder
/// - mobile free -> BannerAd
/// - premium/staff/admin/owner -> SizedBox.shrink
class AdBanner extends ConsumerStatefulWidget {
  const AdBanner({super.key});

  @override
  ConsumerState<AdBanner> createState() => _AdBannerState();
}

class _AdBannerState extends ConsumerState<AdBanner> {
  BannerAd? _bannerAd;
  bool _isLoaded = false;

  @override
  void initState() {
    super.initState();
    if (kIsWeb) return;
    _loadBanner();
  }

  void _loadBanner() {
    try {
      _bannerAd = AdsService.instance.createBannerAd(
        onAdLoaded: (_) {
          if (mounted) setState(() => _isLoaded = true);
        },
        onAdFailed: (_, __) {
          if (mounted) setState(() => _isLoaded = false);
        },
      );
    } on Object {
      // AdMob belum init / sandbox — silent.
    }
  }

  @override
  void dispose() {
    _bannerAd?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (kIsWeb) return const WebAdSlot();

    final gate = ref.watch(adsGateProvider);
    if (!gate.shouldShowAds) return const SizedBox.shrink();

    if (!_isLoaded || _bannerAd == null) {
      return const SizedBox(height: 50); // placeholder height to avoid layout shift
    }

    return SizedBox(
      width: _bannerAd!.size.width.toDouble(),
      height: _bannerAd!.size.height.toDouble(),
      child: AdWidget(ad: _bannerAd!),
    );
  }
}
