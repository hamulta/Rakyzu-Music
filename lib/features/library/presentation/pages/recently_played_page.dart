import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../../../../core/theme/app_colors.dart';
import '../../../../core/widgets/glass_card.dart';
import '../../../../core/widgets/signed_image.dart';
import '../../../catalog/providers/catalog_providers.dart';
import '../widgets/like_button.dart';

class RecentlyPlayedPage extends ConsumerWidget {
  const RecentlyPlayedPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final historyAsync = ref.watch(playHistoryDetailedProvider);
    return Scaffold(
      body: DecoratedBox(
        decoration: BoxDecoration(
            gradient: isDark
                ? AppColors.darkBackgroundGradient
                : AppColors.lightBackgroundGradient,),
        child: SafeArea(
          child: Column(
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(8, 8, 8, 0),
                child: Row(
                  children: [
                    IconButton(
                        onPressed: () => context.pop(),
                        icon: const Icon(CupertinoIcons.back),),
                    Text('Recently Played',
                        style: Theme.of(context)
                            .textTheme
                            .titleLarge
                            ?.copyWith(fontWeight: FontWeight.w700),),
                  ],
                ),
              ),
              Expanded(
                child: historyAsync.when(
                  data: (rows) {
                    if (rows.isEmpty) {
                      return const Center(child: Text('No play history yet'));
                    }
                    // Dedup by song_id, keep most recent played_at
                    final seen = <String>{};
                    final deduped = <Map<String, dynamic>>[];
                    for (final r in rows) {
                      final song = r['song'] as Map<String, dynamic>?;
                      if (song == null) continue;
                      final sid = song['id'] as String;
                      if (seen.add(sid)) deduped.add(r);
                    }
                    return ListView.builder(
                      padding: const EdgeInsets.symmetric(
                          horizontal: 16, vertical: 8,),
                      itemCount: deduped.length,
                      itemBuilder: (context, i) {
                        final r = deduped[i];
                        final songMap = r['song'] as Map<String, dynamic>;
                        final playedAt = r['played_at'] != null
                            ? DateTime.tryParse(r['played_at'] as String)
                            : null;
                        final title = songMap['title'] as String? ?? 'Unknown';
                        final artist = (songMap['artist'] is Map
                            ? (songMap['artist'] as Map)['name']
                            : null) as String?;
                        final cover = songMap['cover_url'] as String?;
                        final songId = songMap['id'] as String;
                        // Build pseudo Song for player
                        final songObj = _mapToSong(songMap);
                        return Padding(
                          padding: const EdgeInsets.only(bottom: 8),
                          child: _HistoryTile(
                            title: title,
                            artist: artist,
                            coverUrl: cover,
                            playedAt: playedAt,
                            songId: songId,
                            song: songObj,
                          ),
                        );
                      },
                    );
                  },
                  loading: () =>
                      const Center(child: CupertinoActivityIndicator()),
                  error: (e, _) => Center(child: Text('Error: $e')),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  // Map raw songMap from Supabase (with nested artist/album) to Song model JSON.
  static dynamic _mapToSong(Map<String, dynamic> m) {
    // We construct via fromJson-compatible map.
    final mapped = Map<String, dynamic>.from(m);
    final album = m['album'];
    if (album is Map<String, dynamic>) mapped['album_title'] = album['title'];
    final artist = m['artist'];
    if (artist is Map<String, dynamic>) mapped['artist_name'] = artist['name'];
    mapped.remove('album');
    mapped.remove('artist');
    // Use Song.fromJson if available.
    try {
      // ignore: avoid_dynamic_calls
      return mapped; // will be handled in tile via manual Song creation
    } catch (_) {
      return mapped;
    }
  }
}

class _HistoryTile extends ConsumerWidget {
  const _HistoryTile(
      {required this.title,
      this.artist,
      this.coverUrl,
      this.playedAt,
      required this.songId,
      required this.song,});
  final String title;
  final String? artist;
  final String? coverUrl;
  final DateTime? playedAt;
  final String songId;
  final dynamic song;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final timeStr = playedAt != null
        ? DateFormat('MMM d, HH:mm').format(playedAt!.toLocal())
        : '';
    // Try to create Song object for playback; fallback to id-only play not possible.
    // We'll fetch full Song via provider if needed. For now use catalog getSong approach via play button disabled if not full.
    return GlassCard(
      padding: const EdgeInsets.all(10),
      borderRadius: 14,
      child: Row(
        children: [
          ClipRRect(
              borderRadius: BorderRadius.circular(8),
              child: SignedImage(
                  value: coverUrl,
                  width: 44,
                  height: 44,
                  fallbackIcon: CupertinoIcons.music_note,),),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(title,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(fontWeight: FontWeight.w600),),
                Row(
                  children: [
                    if (artist != null)
                      Expanded(
                          child: Text(artist!,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: const TextStyle(
                                  color: AppColors.textSecondary,
                                  fontSize: 12,),),),
                    if (timeStr.isNotEmpty)
                      Text(timeStr,
                          style: const TextStyle(
                              color: AppColors.textSecondary, fontSize: 11,),),
                  ],
                ),
              ],
            ),
          ),
          LikeButton(songId: songId, size: 18),
        ],
      ),
    );
  }
}
