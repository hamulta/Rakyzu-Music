import 'package:flutter/cupertino.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/theme/app_colors.dart';
import '../../providers/subscription_provider.dart';

class RenewalReminderBanner extends ConsumerWidget {
  const RenewalReminderBanner({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final activeAsync = ref.watch(activeSubscriptionProvider);
    return activeAsync.when(
      data: (sub) {
        if (sub == null || sub.endDate == null) return const SizedBox.shrink();
        final daysLeft = sub.endDate!.difference(DateTime.now()).inDays;
        if (daysLeft > 3 || daysLeft < 0) return const SizedBox.shrink();
        return Container(
          margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
          padding: const EdgeInsets.all(12),
          decoration: BoxDecoration(color: AppColors.accentWarning.withOpacity(0.15), borderRadius: BorderRadius.circular(12), border: Border.all(color: AppColors.accentWarning.withOpacity(0.4))),
          child: Row(children: [const Icon(CupertinoIcons.time, size: 16, color: Color(0xFF8A6D00)), const SizedBox(width: 8), Expanded(child: Text('Premium expires in $daysLeft day(s) • ${sub.endDate!.toLocal().toString().split(' ').first}', style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600))), Text(sub.status, style: const TextStyle(fontSize: 11, color: AppColors.textSecondary))]),
        );
      },
      loading: () => const SizedBox.shrink(),
      error: (_, __) => const SizedBox.shrink(),
    );
  }
}
