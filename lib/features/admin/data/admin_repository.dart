import 'package:supabase_flutter/supabase_flutter.dart';

class AdminRepository {
  AdminRepository(this._supabase);
  final SupabaseClient _supabase;

  Future<Map<String, int>> getUserCountsPerRole() async {
    final rows = await _supabase.from('users').select('role');
    final map = <String, int>{
      'free': 0,
      'premium': 0,
      'staff': 0,
      'admin': 0,
      'owner': 0
    };
    for (final r in rows) {
      final role = (r as Map)['role'] as String? ?? 'free';
      map[role] = (map[role] ?? 0) + 1;
    }
    return map;
  }

  Future<int> getTotalStreams() async {
    // aggregate songs.play_count fallback to count play_history
    try {
      final rows = await _supabase.from('songs').select('play_count');
      var sum = 0;
      for (final r in rows) {
        sum += (r as Map)['play_count'] as int? ?? 0;
      }
      if (sum > 0) return sum;
    } catch (_) {}
    final rows = await _supabase.from('play_history').select('id');
    return (rows as List).length;
  }

  Future<List<Map<String, dynamic>>> getTopSongs({int limit = 10}) async {
    final rows = await _supabase
        .from('songs')
        .select('id,title,artist_id,play_count,artist:artists(name)')
        .order('play_count', ascending: false)
        .limit(limit);
    return (rows as List).cast<Map<String, dynamic>>();
  }

  Future<List<Map<String, dynamic>>> getUsers(
      {String? search, String? role}) async {
    var q = _supabase
        .from('users')
        .select('id,username,email,full_name,role,is_banned,created_at');
    if (search != null && search.trim().isNotEmpty)
      q = q.ilike('email', '%${search.trim()}%');
    if (role != null && role.isNotEmpty) q = q.eq('role', role);
    final rows = await q.order('created_at', ascending: false).limit(100);
    return (rows as List).cast<Map<String, dynamic>>();
  }

  Future<void> setUserBanned(String uid, bool banned) async {
    await _supabase.from('users').update({'is_banned': banned}).eq('id', uid);
  }

  Future<void> setUserRole(String uid, String newRole) async {
    await _supabase.from('users').update({'role': newRole}).eq('id', uid);
  }

  Future<Map<String, dynamic>> getRevenueStats() async {
    final rows = await _supabase
        .from('subscriptions')
        .select('plan_type,status')
        .eq('status', 'active');
    var totalRevenue = 0;
    var monthly = 0;
    var yearly = 0;
    for (final r in rows as List) {
      final m = r as Map<String, dynamic>;
      final plan = m['plan_type'] as String;
      if (plan == 'monthly') {
        totalRevenue += 49000;
        monthly++;
      } else if (plan == 'yearly') {
        totalRevenue += 449000;
        yearly++;
      }
    }
    final mrr = monthly * 49000 + yearly * 449000 ~/ 12;
    return {
      'totalRevenue': totalRevenue,
      'activeCount': rows.length,
      'monthly': monthly,
      'yearly': yearly,
      'mrr': mrr
    };
  }

  Future<List<Map<String, dynamic>>> getPricingPlans() async {
    final rows =
        await _supabase.from('pricing_plans').select().order('price_idr');
    return (rows as List).cast<Map<String, dynamic>>();
  }

  Future<void> updatePricing(String name, int price) async {
    await _supabase.from('pricing_plans').update({
      'price_idr': price,
      'updated_at': DateTime.now().toIso8601String()
    }).eq('name', name);
  }

  Future<List<Map<String, dynamic>>> getAdImpressions({int days = 7}) async {
    final since =
        DateTime.now().subtract(Duration(days: days)).toIso8601String();
    final rows = await _supabase
        .from('ad_impressions')
        .select('ad_type,created_at')
        .gte('created_at', since)
        .order('created_at');
    return (rows as List).cast<Map<String, dynamic>>();
  }
}
