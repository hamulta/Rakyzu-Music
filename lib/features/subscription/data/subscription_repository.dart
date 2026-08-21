import 'package:supabase_flutter/supabase_flutter.dart';

import '../models/subscription_model.dart';

class SubscriptionRepository {
  SubscriptionRepository(this._supabase);
  final SupabaseClient _supabase;

  String? get _uid => _supabase.auth.currentUser?.id;

  Future<List<SubscriptionModel>> getMySubscriptions() async {
    final uid = _uid;
    if (uid == null) return [];
    final rows = await _supabase.from('subscriptions').select().eq('user_id', uid).order('created_at', ascending: false);
    return rows.map((e) => SubscriptionModel.fromJson(e as Map<String, dynamic>)).toList();
  }

  Future<SubscriptionModel?> getActiveSubscription() async {
    final uid = _uid;
    if (uid == null) return null;
    final row = await _supabase.from('subscriptions').select().eq('user_id', uid).eq('status', 'active').order('end_date', ascending: false).limit(1).maybeSingle();
    if (row == null) return null;
    return SubscriptionModel.fromJson(row as Map<String, dynamic>);
  }

  /// Request snap_token dari Edge Function (server-side, server key tidak di client).
  Future<Map<String, dynamic>> requestSnapToken({required String planType}) async {
    final res = await _supabase.functions.invoke('create-snap-token', body: {'plan_type': planType});
    if (res.status != 200) throw Exception(res.data?['error'] ?? 'Failed to create snap token');
    return res.data as Map<String, dynamic>;
  }

  Future<void> cancelSubscription(String id) async {
    await _supabase.from('subscriptions').update({'status': 'cancelled'}).eq('id', id);
  }
}
