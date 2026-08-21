import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/theme/app_colors.dart';
import '../../../../core/widgets/glass_card.dart';
import '../../../../core/widgets/signed_image.dart';
import '../../../catalog/providers/catalog_providers.dart';
import '../../../library/models/playlist.dart';
import '../../../library/providers/playlist_providers.dart';

enum LibraryFilter { all, playlists, artists }

class LibraryTab extends ConsumerStatefulWidget {
  const LibraryTab({super.key});

  @override
  ConsumerState<LibraryTab> createState() => _LibraryTabState();
}

class _LibraryTabState extends ConsumerState<LibraryTab> {
  LibraryFilter _filter = LibraryFilter.all;

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final myPlaylistsAsync = ref.watch(myPlaylistsProvider);
    final likedCountAsync = ref.watch(likedSongsCountProvider);
    final followedAsync = ref.watch(followedArtistsProvider);
    final recentAsync = ref.watch(recentlyPlayedProvider);

    final showPlaylists =
        _filter == LibraryFilter.all || _filter == LibraryFilter.playlists;
    final showArtists =
        _filter == LibraryFilter.all || _filter == LibraryFilter.artists;
    final showLikedAndRecent = _filter == LibraryFilter.all;

    return Scaffold(
      body: DecoratedBox(
        decoration: BoxDecoration(
          gradient: isDark
              ? AppColors.darkBackgroundGradient
              : AppColors.lightBackgroundGradient,
        ),
        child: SafeArea(
          child: CustomScrollView(
            slivers: [
              SliverToBoxAdapter(
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(20, 16, 20, 12),
                  child: Row(
                    children: [
                      Text('Library',
                          style: Theme.of(context).textTheme.headlineLarge,),
                      const Spacer(),
                      if (showPlaylists)
                        IconButton(
                          onPressed: () =>
                              context.push('/library/playlist/create'),
                          icon: const Icon(CupertinoIcons.add_circled,
                              color: AppColors.azureMistDeep,),
                        ),
                    ],
                  ),
                ),
              ),
              SliverToBoxAdapter(
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 20),
                  child: _FilterChips(
                    selected: _filter,
                    onSelected: (f) => setState(() => _filter = f),
                  ),
                ),
              ),
              const SliverToBoxAdapter(child: SizedBox(height: 12)),
              if (showLikedAndRecent) ...[
                SliverToBoxAdapter(
                  child: Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 20),
                    child: _LikedSongsEntry(countAsync: likedCountAsync),
                  ),
                ),
                const SliverToBoxAdapter(child: SizedBox(height: 12)),
              ],
              if (showPlaylists) ...[
                SliverToBoxAdapter(
                  child: Padding(
                    padding: const EdgeInsets.fromLTRB(20, 8, 20, 8),
                    child: Row(
                      children: [
                        Text('Playlists',
                            style: Theme.of(context)
                                .textTheme
                                .titleMedium
                                ?.copyWith(fontWeight: FontWeight.w700),),
                        const Spacer(),
                        myPlaylistsAsync.when(
                          data: (list) => Text('${list.length}',
                              style: const TextStyle(
                                  color: AppColors.textSecondary,
                                  fontSize: 13,),),
                          loading: () => const SizedBox.shrink(),
                          error: (_, __) => const SizedBox.shrink(),
                        ),
                      ],
                    ),
                  ),
                ),
                _PlaylistsSection(playlistsAsync: myPlaylistsAsync),
                const SliverToBoxAdapter(child: SizedBox(height: 8)),
              ],
              if (showArtists) ...[
                SliverToBoxAdapter(
                  child: Padding(
                    padding: const EdgeInsets.fromLTRB(20, 8, 20, 8),
                    child: Row(
                      children: [
                        Text('Following',
                            style: Theme.of(context)
                                .textTheme
                                .titleMedium
                                ?.copyWith(fontWeight: FontWeight.w700),),
                        const Spacer(),
                        followedAsync.when(
                          data: (l) => Text('${l.length}',
                              style: const TextStyle(
                                  color: AppColors.textSecondary,
                                  fontSize: 13,),),
                          loading: () => const SizedBox.shrink(),
                          error: (_, __) => const SizedBox.shrink(),
                        ),
                      ],
                    ),
                  ),
                ),
                _FollowingSection(followedAsync: followedAsync),
                const SliverToBoxAdapter(child: SizedBox(height: 8)),
              ],
              if (showLikedAndRecent) ...[
                SliverToBoxAdapter(
                  child: Padding(
                    padding: const EdgeInsets.fromLTRB(20, 8, 20, 8),
                    child: Text('Recently Played',
                        style: Theme.of(context)
                            .textTheme
                            .titleMedium
                            ?.copyWith(fontWeight: FontWeight.w700),),
                  ),
                ),
                _RecentSection(recentAsync: recentAsync),
                SliverToBoxAdapter(
                  child: Padding(
                    padding:
                        const EdgeInsets.symmetric(horizontal: 20, vertical: 8),
                    child: TextButton(
                      onPressed: () => context.push('/library/history'),
                      child: const Text('View full history'),
                    ),
                  ),
                ),
              ],
              const SliverToBoxAdapter(child: SizedBox(height: 100)),
            ],
          ),
        ),
      ),
    );
  }
}

class _FilterChips extends StatelessWidget {
  const _FilterChips({required this.selected, required this.onSelected});
  final LibraryFilter selected;
  final ValueChanged<LibraryFilter> onSelected;

  @override
  Widget build(BuildContext context) {
    Widget chip(LibraryFilter f, String label) {
      final isSel = selected == f;
      return ChoiceChip(
        label: Text(label),
        selected: isSel,
        onSelected: (_) => onSelected(f),
        selectedColor: AppColors.azureMistDeep.withOpacity(0.2),
        labelStyle: TextStyle(
            color: isSel ? AppColors.azureMistDeep : AppColors.textSecondary,
            fontWeight: isSel ? FontWeight.w600 : FontWeight.w400,),
      );
    }

    return Wrap(
      spacing: 8,
      children: [
        chip(LibraryFilter.all, 'All'),
        chip(LibraryFilter.playlists, 'Playlists'),
        chip(LibraryFilter.artists, 'Artists'),
      ],
    );
  }
}

class _LikedSongsEntry extends StatelessWidget {
  const _LikedSongsEntry({required this.countAsync});
  final AsyncValue<int> countAsync;

  @override
  Widget build(BuildContext context) {
    final count = countAsync.valueOrNull;
    return GlassCard(
      borderRadius: 20,
      padding: const EdgeInsets.all(16),
      onTap: () => context.push('/library/liked'),
      child: Row(
        children: [
          Container(
            width: 52,
            height: 52,
            decoration: BoxDecoration(
              gradient: const LinearGradient(
                  colors: [Color(0xFF7FB3E8), Color(0xFF5A9BD8)],),
              borderRadius: BorderRadius.circular(12),
            ),
            child: const Icon(CupertinoIcons.heart_fill, color: Colors.white),
          ),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Liked Songs',
                    style: Theme.of(context)
                        .textTheme
                        .titleMedium
                        ?.copyWith(fontWeight: FontWeight.w600),),
                const SizedBox(height: 2),
                Text(count == null ? '—' : '$count songs',
                    style: const TextStyle(
                        color: AppColors.textSecondary, fontSize: 12,),),
              ],
            ),
          ),
          const Icon(CupertinoIcons.chevron_right,
              color: AppColors.textSecondary, size: 18,),
        ],
      ),
    );
  }
}

class _PlaylistsSection extends StatelessWidget {
  const _PlaylistsSection({required this.playlistsAsync});
  final AsyncValue<List<Playlist>> playlistsAsync;

  @override
  Widget build(BuildContext context) {
    return playlistsAsync.when(
      data: (list) {
        if (list.isEmpty) {
          return SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 8),
              child: GlassCard(
                padding: const EdgeInsets.all(20),
                child: Column(
                  children: [
                    const Icon(CupertinoIcons.music_note_list,
                        color: AppColors.textSecondary,),
                    const SizedBox(height: 8),
                    Text('Belum ada playlist',
                        style: Theme.of(context).textTheme.bodyMedium,),
                    const SizedBox(height: 8),
                    CupertinoButton(
                      padding: EdgeInsets.zero,
                      onPressed: () => context.push('/library/playlist/create'),
                      child: const Text('Create playlist'),
                    ),
                  ],
                ),
              ),
            ),
          );
        }
        return SliverList(
          delegate: SliverChildBuilderDelegate(
            (context, i) {
              final pl = list[i];
              return Padding(
                padding:
                    const EdgeInsets.symmetric(horizontal: 20, vertical: 6),
                child: GlassCard(
                  borderRadius: 16,
                  padding: const EdgeInsets.all(12),
                  onTap: () => context.push('/library/playlist/${pl.id}'),
                  child: Row(
                    children: [
                      ClipRRect(
                        borderRadius: BorderRadius.circular(8),
                        child: pl.coverUrl != null && pl.coverUrl!.isNotEmpty
                            ? SignedImage(
                                value: pl.coverUrl,
                                width: 48,
                                height: 48,
                                fallbackIcon: CupertinoIcons.music_note_list,)
                            : Container(
                                width: 48,
                                height: 48,
                                color:
                                    AppColors.azureMistDeep.withOpacity(0.15),
                                child: const Icon(
                                    CupertinoIcons.music_note_list,
                                    color: AppColors.azureMistDeep,),
                              ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(pl.name,
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: const TextStyle(
                                    fontWeight: FontWeight.w600,),),
                            const SizedBox(height: 2),
                            Text(
                              '${pl.songCount} songs • ${pl.isPublic ? 'Public' : 'Private'}',
                              style: const TextStyle(
                                  color: AppColors.textSecondary, fontSize: 12,),
                            ),
                          ],
                        ),
                      ),
                      const Icon(CupertinoIcons.chevron_right,
                          size: 16, color: AppColors.textSecondary,),
                    ],
                  ),
                ),
              );
            },
            childCount: list.length,
          ),
        );
      },
      loading: () => const SliverToBoxAdapter(
          child: Padding(
              padding: EdgeInsets.all(20),
              child: Center(child: CupertinoActivityIndicator()),),),
      error: (e, _) => SliverToBoxAdapter(
          child: Padding(
              padding: const EdgeInsets.all(20), child: Text('Error: $e'),),),
    );
  }
}

class _FollowingSection extends StatelessWidget {
  const _FollowingSection({required this.followedAsync});
  final AsyncValue followedAsync;

  @override
  Widget build(BuildContext context) {
    return followedAsync.when(
      data: (list) {
        final artists = list as List;
        if (artists.isEmpty) {
          return const SliverToBoxAdapter(
            child: Padding(
              padding: EdgeInsets.symmetric(horizontal: 20, vertical: 8),
              child: GlassCard(
                  padding: EdgeInsets.all(16),
                  child:
                      Text('Belum follow artist', textAlign: TextAlign.center),),
            ),
          );
        }
        return SliverToBoxAdapter(
          child: SizedBox(
            height: 92,
            child: ListView.separated(
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.symmetric(horizontal: 20),
              itemCount: artists.length,
              separatorBuilder: (_, __) => const SizedBox(width: 12),
              itemBuilder: (context, i) {
                final a = artists[i];
                return GestureDetector(
                  onTap: () => context.push('/artist/${a.id}'),
                  child: Column(
                    children: [
                      ClipOval(
                          child: SignedImage(
                              value: a.imageUrl,
                              width: 64,
                              height: 64,
                              fallbackIcon: CupertinoIcons.person_fill,),),
                      const SizedBox(height: 6),
                      SizedBox(
                          width: 72,
                          child: Text(a.name,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              textAlign: TextAlign.center,
                              style: const TextStyle(fontSize: 12),),),
                    ],
                  ),
                );
              },
            ),
          ),
        );
      },
      loading: () => const SliverToBoxAdapter(
          child: SizedBox(
              height: 64, child: Center(child: CupertinoActivityIndicator()),),),
      error: (e, _) => SliverToBoxAdapter(
          child: Padding(
              padding: const EdgeInsets.all(20), child: Text('Error: $e'),),),
    );
  }
}

class _RecentSection extends StatelessWidget {
  const _RecentSection({required this.recentAsync});
  final AsyncValue recentAsync;

  @override
  Widget build(BuildContext context) {
    return recentAsync.when(
      data: (list) {
        final songs = list as List;
        if (songs.isEmpty) {
          return const SliverToBoxAdapter(
              child: Padding(
                  padding: EdgeInsets.symmetric(horizontal: 20),
                  child: Text('No recent plays',
                      style: TextStyle(color: AppColors.textSecondary),),),);
        }
        return SliverToBoxAdapter(
          child: SizedBox(
            height: 140,
            child: ListView.separated(
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.symmetric(horizontal: 20),
              itemCount: songs.length > 10 ? 10 : songs.length,
              separatorBuilder: (_, __) => const SizedBox(width: 12),
              itemBuilder: (context, i) {
                final s = songs[i];
                return SizedBox(
                  width: 110,
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      ClipRRect(
                        borderRadius: BorderRadius.circular(10),
                        child: SignedImage(
                            value: s.coverUrl,
                            width: 110,
                            height: 110,
                            fallbackIcon: CupertinoIcons.music_note,),
                      ),
                      const SizedBox(height: 6),
                      Text(s.title,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                              fontSize: 12, fontWeight: FontWeight.w600,),),
                    ],
                  ),
                );
              },
            ),
          ),
        );
      },
      loading: () => const SliverToBoxAdapter(
          child: SizedBox(
              height: 80, child: Center(child: CupertinoActivityIndicator()),),),
      error: (e, _) => SliverToBoxAdapter(
          child: Padding(
              padding: const EdgeInsets.all(20), child: Text('Error: $e'),),),
    );
  }
}
