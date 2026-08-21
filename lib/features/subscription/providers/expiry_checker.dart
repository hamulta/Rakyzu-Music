import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'subscription_provider.dart';
import '../../../shared/providers/supabase_providers.dart';

/// Cek expiry saat app dibuka: jika active subscription sudah lewat end_date,
/// panggil edge function check-expired atau cukup invalidate.
/// AdsGateProvider otomatis merespons karena users.role berubah via webhook/cron.
final expiryCheckerProvider = FutureProvider<void>((ref) async {
  final active = await ref.watch(activeSubscriptionProvider.future);
  if (active == null) return;
  if (active.endDate != null && active.endDate!.isBefore(DateTime.now())) {
    // Best-effort: trigger server check (silent).
    try {
      final supa = ref.read(supabaseProvider);
      await supa.functions.invoke('check-expired-subscriptions');
    } catch (_) {}
  }
});
