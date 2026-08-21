import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:share_plus/share_plus.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/widgets/glass_card.dart';
import '../../providers/admin_providers.dart';
import '../../utils/csv_export.dart';

class AnalyticsPage extends ConsumerWidget {
  const AnalyticsPage({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final users = ref.watch(adminUserCountsProvider);
    final streams = ref.watch(adminTotalStreamsProvider);
    final top = ref.watch(adminTopSongsProvider);
    return ListView(padding: const EdgeInsets.all(20), children: [
      Text('Analytics', style: Theme.of(context).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.w800)),
      const SizedBox(height:16),
      users.when(data: (m)=> GlassCard(padding: const EdgeInsets.all(16), child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [const Text('Total Users per Role', style: TextStyle(fontWeight:FontWeight.w700)), const SizedBox(height:8), Wrap(spacing:12, children: m.entries.map((e)=> Chip(label: Text('${e.key}: ${e.value}'))).toList())])), loading: ()=> const CupertinoActivityIndicator(), error: (e,_ )=> Text('Error $e')),
      const SizedBox(height:16),
      streams.when(data: (v)=> GlassCard(padding: const EdgeInsets.all(16), child: Row(children:[ const Icon(CupertinoIcons.play_fill, color: AppColors.azureMistDeep), const SizedBox(width:8), Text('Total Streams: $v', style: const TextStyle(fontWeight:FontWeight.w600))])), loading: ()=> const CupertinoActivityIndicator(), error: (e,_ )=> Text('Error $e')),
      const SizedBox(height:16),
      Row(children: [Text('Top Songs', style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700)), const Spacer(), IconButton(icon: const Icon(CupertinoIcons.share), onPressed: () async { final data = await ref.read(adminTopSongsProvider.future); final csv = toCsv(data); await Share.share('Top Songs CSV\n$csv'); })]),
      const SizedBox(height:8),
      top.when(data: (list)=> GlassCard(padding: EdgeInsets.zero, child: Column(children: list.asMap().entries.map((e)=> ListTile(leading: Text('#${e.key+1}'), title: Text(e.value['title']??''), subtitle: Text('artist: ${e.value['artist']?['name'] ?? '-'}'), trailing: Text('${e.value['play_count']??0} plays'))).toList())), loading: ()=> const CupertinoActivityIndicator(), error: (e,_ )=> Text('Error $e')),
    ],);
  }
}
