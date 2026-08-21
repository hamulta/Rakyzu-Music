import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../../../../core/theme/app_colors.dart';
import '../../../../core/widgets/glass_card.dart';
import '../../providers/subscription_provider.dart';

class TransactionHistoryPage extends ConsumerWidget {
  const TransactionHistoryPage({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final listAsync = ref.watch(mySubscriptionsProvider);
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return Scaffold(
      body: DecoratedBox(
        decoration: BoxDecoration(gradient: isDark ? AppColors.darkBackgroundGradient : AppColors.lightBackgroundGradient),
        child: SafeArea(
          child: Column(children: [
            Padding(padding: const EdgeInsets.fromLTRB(8, 8, 8, 0), child: Row(children: [IconButton(onPressed: () => context.pop(), icon: const Icon(CupertinoIcons.back)), Text('Transaction History', style: Theme.of(context).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w700))])),
            Expanded(
              child: listAsync.when(
                data: (list) {
                  if (list.isEmpty) return const Center(child: Text('No transactions yet'));
                  return ListView.builder(
                    padding: const EdgeInsets.all(16),
                    itemCount: list.length,
                    itemBuilder: (_, i) {
                      final s = list[i];
                      final d = s.createdAt != null ? DateFormat('dd MMM yyyy, HH:mm').format(s.createdAt!.toLocal()) : '-';
                      final price = s.planType == 'yearly' ? 'Rp 449.000' : 'Rp 49.000';
                      return Padding(
                        padding: const EdgeInsets.only(bottom: 10),
                        child: GlassCard(
                          padding: const EdgeInsets.all(14),
                          child: Row(children: [
                            Icon(s.isActive ? CupertinoIcons.checkmark_seal_fill : s.isPending ? CupertinoIcons.hourglass : CupertinoIcons.xmark_circle_fill, color: s.isActive ? Colors.green : s.isPending ? Colors.orange : Colors.red),
                            const SizedBox(width: 12),
                            Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Text('${s.planType} • $price', style: const TextStyle(fontWeight: FontWeight.w700)), Text(d, style: const TextStyle(color: AppColors.textSecondary, fontSize: 12)), Text('Status: ${s.status}', style: const TextStyle(fontSize: 12))])),
                            Text(s.transactionId?.substring(0, 8) ?? '-', style: const TextStyle(color: AppColors.textSecondary, fontSize: 11)),
                          ],),
                        ),
                      );
                    },
                  );
                },
                loading: () => const Center(child: CupertinoActivityIndicator()),
                error: (e, _) => Center(child: Text('Error: $e')),
              ),
            ),
          ],),
        ),
      ),
    );
  }
}
