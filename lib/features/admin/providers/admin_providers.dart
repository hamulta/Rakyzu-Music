import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../shared/providers/supabase_providers.dart';
import '../data/admin_repository.dart';

final adminRepositoryProvider = Provider<AdminRepository>(
    (ref) => AdminRepository(ref.watch(supabaseProvider)));

final adminUserCountsProvider = FutureProvider<Map<String, int>>(
    (ref) => ref.watch(adminRepositoryProvider).getUserCountsPerRole());
final adminTotalStreamsProvider = FutureProvider<int>(
    (ref) => ref.watch(adminRepositoryProvider).getTotalStreams());
final adminTopSongsProvider = FutureProvider<List<Map<String, dynamic>>>(
    (ref) => ref.watch(adminRepositoryProvider).getTopSongs());
final adminRevenueProvider = FutureProvider<Map<String, dynamic>>(
    (ref) => ref.watch(adminRepositoryProvider).getRevenueStats());
final adminPricingProvider = FutureProvider<List<Map<String, dynamic>>>(
    (ref) => ref.watch(adminRepositoryProvider).getPricingPlans());
final adminAdImpressionsProvider =
    FutureProvider.family<List<Map<String, dynamic>>, int>((ref, days) =>
        ref.watch(adminRepositoryProvider).getAdImpressions(days: days));

final adminAllSongsProvider = FutureProvider<List<dynamic>>((ref) async {
  // Use supabase directly to fetch all songs including unpublished for admin view
  final supa = ref.watch(supabaseProvider);
  final rows = await supa
      .from('songs')
      .select('id,title,artist_id,is_published,artist:artists(name)')
      .order('created_at', ascending: false)
      .limit(100);
  return (rows as List).cast<Map<String, dynamic>>();
});
