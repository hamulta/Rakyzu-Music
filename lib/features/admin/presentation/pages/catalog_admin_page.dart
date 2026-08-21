import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/constants/app_routes.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/widgets/glass_card.dart';
import '../../../catalog/providers/catalog_providers.dart';
import '../../providers/admin_providers.dart';

class CatalogAdminPage extends ConsumerWidget {
  const CatalogAdminPage({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final artists = ref.watch(artistsControllerProvider).valueOrNull ?? [];
    final albums = ref.watch(albumsControllerProvider).valueOrNull ?? [];
    final songsAsync = ref.watch(adminAllSongsProvider);
    return ListView(padding: const EdgeInsets.all(20), children: [
      Text('Catalog Management', style: Theme.of(context).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.w800)),
      const SizedBox(height:8), const Text('Admin/Owner melihat semua konten lintas staff; Staff hanya miliknya (RLS).', style: TextStyle(color: AppColors.textSecondary, fontSize:12)),
      const SizedBox(height:16),
      _section(context, 'Artists', artists.length, AppRoutes.catalogArtists, artists.map((a)=> _Row(title: a.name, subtitle: a.id)).toList()),
      const SizedBox(height:16),
      _section(context, 'Albums', albums.length, AppRoutes.catalogAlbums, albums.map((a)=> _Row(title: a.title, subtitle: a.artistName??'')).toList()),
      const SizedBox(height:16),
      songsAsync.when(
        data: (songs)=> _SongsAdminSection(songs: songs),
        loading: ()=> const Center(child: CupertinoActivityIndicator()),
        error: (e,_)=> Text('Error $e'),
      ),
    ],);
  }
  Widget _section(BuildContext ctx, String title, int count, String route, List<_Row> rows)=> Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
    Row(children: [Text('$title ($count)', style: const TextStyle(fontWeight:FontWeight.w700)), const Spacer(), TextButton(onPressed: ()=> ctx.go(route), child: const Text('View All'))]),
    GlassCard(padding: EdgeInsets.zero, child: Column(children: rows.take(5).map((r)=> ListTile(title: Text(r.title, style: const TextStyle(fontSize:13)), subtitle: Text(r.subtitle, style: const TextStyle(fontSize:11, color: AppColors.textSecondary)))).toList())),
  ],);
}
class _Row{ const _Row({required this.title, required this.subtitle}); final String title; final String subtitle; }

class _SongsAdminSection extends ConsumerWidget {
  const _SongsAdminSection({required this.songs});
  final List<dynamic> songs;
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Row(children: [Text('Songs (${songs.length})', style: const TextStyle(fontWeight:FontWeight.w700)), const Spacer(), TextButton(onPressed: ()=> context.go(AppRoutes.catalogSongs), child: const Text('View All'))]),
      GlassCard(padding: EdgeInsets.zero, child: Column(children: songs.take(5).map((e){
        final m = e as Map<String,dynamic>;
        final title = m['title'] as String? ?? '';
        final isPub = m['is_published'] as bool? ?? true;
        final artistName = (m['artist'] as Map?)?['name'] as String? ?? '';
        final id = m['id'] as String;
        return ListTile(
          title: Text(title, style: const TextStyle(fontSize:13)),
          subtitle: Text('$artistName • ${isPub ? 'published' : 'unpublished'}', style: const TextStyle(fontSize:11, color: AppColors.textSecondary)),
          trailing: CupertinoSwitch(value: isPub, onChanged: (v) async {
            try{ await ref.read(catalogRepositoryProvider).setSongPublished(id, v); ref.invalidate(adminAllSongsProvider); ref.invalidate(songsControllerProvider); }catch(e){ if(context.mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Failed: $e'))); }
          },),
        );
      }).toList(),),),
      const Padding(padding: EdgeInsets.only(top:8), child: Text('Unpublished tidak muncul di Home/Search untuk listener biasa.', style: TextStyle(color: AppColors.textSecondary, fontSize:11))),
    ],);
  }
}
