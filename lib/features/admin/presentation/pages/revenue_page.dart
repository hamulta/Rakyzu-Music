import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/theme/app_colors.dart';
import '../../../../core/widgets/glass_card.dart';
import '../../providers/admin_providers.dart';

class RevenuePage extends ConsumerWidget {
  const RevenuePage({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final rev = ref.watch(adminRevenueProvider);
    return ListView(padding: const EdgeInsets.all(20), children: [
      Text('Revenue', style: Theme.of(context).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.w800)),
      const SizedBox(height:16),
      rev.when(data: (m)=> Column(children: [
        GlassCard(padding: const EdgeInsets.all(16), child: Column(children: [
          Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [const Text('Total Revenue'), Text('Rp ${m['totalRevenue']}', style: const TextStyle(fontWeight:FontWeight.w700, color: AppColors.azureMistDeep))]),
          const Divider(),
          Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [const Text('Active Subscribers'), Text('${m['activeCount']}')]),
          Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [const Text('Monthly'), Text('${m['monthly']}')]),
          Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [const Text('Yearly'), Text('${m['yearly']}')]),
          const Divider(),
          Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [const Text('MRR (est.)'), Text('Rp ${m['mrr']}', style: const TextStyle(fontWeight:FontWeight.w600))]),
        ],),),
      ],), loading: ()=> const CupertinoActivityIndicator(), error: (e,_ )=> Text('Error $e'),),
    ],);
  }
}
