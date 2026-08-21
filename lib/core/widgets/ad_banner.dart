import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:startapp_sdk/startapp.dart';

import '../ads/ads_gate_provider.dart';
import '../ads/ads_service.dart';
import 'web_ad_slot.dart';

/// Banner yang otomatis:
/// - kIsWeb  -> WebAdSlot placeholder (Start.io tidak support Web)
/// - mobile free -> Start.io Banner (App ID 207228132, test mode)
/// - premium/staff/admin/owner -> SizedBox.shrink
/// AdsGateProvider tetap single source, tidak peduli SDK di baliknya.
class AdBanner extends ConsumerStatefulWidget {
  const AdBanner({super.key});
  @override
  ConsumerState<AdBanner> createState() => _AdBannerState();
}

class _AdBannerState extends ConsumerState<AdBanner> {
  StartAppBannerAd? _bannerAd;

  @override
  void initState() {
    super.initState();
    if (kIsWeb) return;
    _load();
  }

  Future<void> _load() async {
    final ad = await AdsService.instance.loadBannerAd();
    if (mounted && ad != null) setState(() => _bannerAd = ad);
  }

  @override
  Widget build(BuildContext context) {
    if (kIsWeb) return const WebAdSlot();
    final gate = ref.watch(adsGateProvider);
    if (!gate.shouldShowAds) return const SizedBox.shrink();
    final ad = _bannerAd;
    if (ad == null) return const SizedBox(height: 50);
    return SizedBox(height: 50, child: StartAppBanner(ad));
  }
}
