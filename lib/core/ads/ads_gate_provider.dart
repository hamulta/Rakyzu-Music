import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../models/app_role.dart';
import '../../features/catalog/providers/role_provider.dart';

/// Single source of truth untuk pertanyaan:
/// "apakah user ini harus lihat ads / kena limit skip?"
///
/// Free tier -> kena ads & limit.
/// Premium/Staff/Admin/Owner -> bypass semua.
final adsGateProvider = Provider<AdsGate>((ref) {
  final roleAsync = ref.watch(currentAppRoleProvider);
  final role = roleAsync.valueOrNull;
  return AdsGate(role: role);
});

class AdsGate {
  AdsGate({required this.role});

  final AppRole? role;

  /// True jika user harus melihat ads.
  bool get shouldShowAds => role == null || role == AppRole.free;

  /// True jika skip limit berlaku.
  bool get shouldEnforceSkipLimit => role == null || role == AppRole.free;

  /// True jika user adalah free tier.
  bool get isFree => role == null || role == AppRole.free;

  bool get isPremiumOrStaff => !isFree;
}
