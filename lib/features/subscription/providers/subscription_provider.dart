import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../shared/providers/supabase_providers.dart';
import '../data/subscription_repository.dart';
import '../models/subscription_model.dart';

final subscriptionRepositoryProvider = Provider<SubscriptionRepository>((ref) => SubscriptionRepository(ref.watch(supabaseProvider)));

final mySubscriptionsProvider = FutureProvider<List<SubscriptionModel>>((ref) async => ref.watch(subscriptionRepositoryProvider).getMySubscriptions());

final activeSubscriptionProvider = FutureProvider<SubscriptionModel?>((ref) async => ref.watch(subscriptionRepositoryProvider).getActiveSubscription());
