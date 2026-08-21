import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/widgets/glass_card.dart';
import '../../providers/admin_providers.dart';

class AdAnalyticsPage extends ConsumerStatefulWidget {
  const AdAnalyticsPage({super.key});
  @override
  ConsumerState<AdAnalyticsPage> createState()=> _AdAnalyticsPageState();
}
class _AdAnalyticsPageState extends ConsumerState<AdAnalyticsPage> {
  int _days=7;
  @override
  Widget build(BuildContext context){
    final data = ref.watch(adminAdImpressionsProvider(_days));
    return ListView(padding: const EdgeInsets.all(20), children: [
      Text('Ad Impressions', style: Theme.of(context).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.w800)),
      const SizedBox(height:8),
      SingleChildScrollView(scrollDirection: Axis.horizontal, child: Row(children: [ChoiceChip(label: const Text('7d'), selected: _days==7, onSelected: (_)=> setState(()=> _days=7)), const SizedBox(width:8), ChoiceChip(label: const Text('30d'), selected: _days==30, onSelected: (_)=> setState(()=> _days=30))])),
      const SizedBox(height:16),
      data.when(data: (list){
        final banner = list.where((e)=> e['ad_type']=='banner').length;
        final inter = list.where((e)=> e['ad_type']=='interstitial').length;
        return Column(children: [
          GlassCard(padding: const EdgeInsets.all(16), child: Row(mainAxisAlignment: MainAxisAlignment.spaceAround, children: [Column(children:[const Text('Banner'), Text('$banner', style: const TextStyle(fontWeight: FontWeight.w800, fontSize:20))]), Column(children:[const Text('Interstitial'), Text('$inter', style: const TextStyle(fontWeight: FontWeight.w800, fontSize:20))]), Column(children:[const Text('Total'), Text('${list.length}', style: const TextStyle(fontWeight: FontWeight.w800, fontSize:20))])])),
          const SizedBox(height:16),
          GlassCard(padding: EdgeInsets.zero, child: Column(children: list.take(20).map((e)=> ListTile(leading: Icon(e['ad_type']=='banner'? Icons.image: Icons.video_library), title: Text(e['ad_type'] as String), subtitle: Text('${e['created_at']}'))).toList())),
        ]);
      }, loading: ()=> const CupertinoActivityIndicator(), error: (e,_ )=> Text('Error $e')),
    ]);
  }
}
