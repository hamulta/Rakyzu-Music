import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/theme/app_colors.dart';
import '../../../../core/widgets/glass_card.dart';
import '../../../catalog/models/song.dart';
import '../../providers/playlist_providers.dart';

class AddToPlaylistSheet extends ConsumerStatefulWidget {
  const AddToPlaylistSheet({super.key, required this.song});
  final Song song;

  static Future<void> show(BuildContext context, Song song) {
    return showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => AddToPlaylistSheet(song: song),
    );
  }

  @override
  ConsumerState<AddToPlaylistSheet> createState() => _AddToPlaylistSheetState();
}

class _AddToPlaylistSheetState extends ConsumerState<AddToPlaylistSheet> {
  final _newNameCtrl = TextEditingController();
  bool _creating = false;

  @override
  void dispose() {
    _newNameCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final playlistsAsync = ref.watch(myPlaylistsProvider);
    return ClipRRect(
      borderRadius: const BorderRadius.vertical(top: Radius.circular(24)),
      child: Container(
        color: isDark ? AppColors.darkSurface : Colors.white,
        padding:
            EdgeInsets.only(bottom: MediaQuery.of(context).viewInsets.bottom),
        child: SafeArea(
          child: Padding(
            padding: const EdgeInsets.all(20),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Center(
                  child: Container(
                    width: 40,
                    height: 4,
                    decoration: BoxDecoration(
                      color: Colors.grey.withOpacity(0.3),
                      borderRadius: BorderRadius.circular(2),
                    ),
                  ),
                ),
                const SizedBox(height: 16),
                Text(
                  'Add to Playlist',
                  style: Theme.of(context)
                      .textTheme
                      .titleLarge
                      ?.copyWith(fontWeight: FontWeight.w700),
                ),
                Text(
                  widget.song.title,
                  style: const TextStyle(color: AppColors.textSecondary),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
                const SizedBox(height: 16),
                playlistsAsync.when(
                  data: (list) {
                    if (list.isEmpty) {
                      return const Text(
                        'No playlists yet — create one below.',
                        style: TextStyle(color: AppColors.textSecondary),
                      );
                    }
                    return Flexible(
                      child: ListView.separated(
                        shrinkWrap: true,
                        itemCount: list.length,
                        separatorBuilder: (_, __) => const SizedBox(height: 8),
                        itemBuilder: (context, i) {
                          final pl = list[i];
                          return GlassCard(
                            padding: const EdgeInsets.all(12),
                            onTap: () async {
                              try {
                                await ref
                                    .read(playlistRepositoryProvider)
                                    .addSongToPlaylist(pl.id, widget.song.id);
                                ref.invalidate(playlistSongsProvider(pl.id));
                                ref.invalidate(myPlaylistsProvider);
                                if (mounted) {
                                  Navigator.pop(context);
                                  ScaffoldMessenger.of(context).showSnackBar(
                                    SnackBar(
                                      content: Text('Added to "${pl.name}"'),
                                    ),
                                  );
                                }
                              } catch (e) {
                                if (mounted) {
                                  ScaffoldMessenger.of(context).showSnackBar(
                                    SnackBar(content: Text('Failed: $e')),
                                  );
                                }
                              }
                            },
                            child: Row(
                              children: [
                                const Icon(
                                  CupertinoIcons.music_note_list,
                                  color: AppColors.azureMistDeep,
                                ),
                                const SizedBox(width: 12),
                                Expanded(
                                  child: Text(
                                    pl.name,
                                    style: const TextStyle(
                                      fontWeight: FontWeight.w600,
                                    ),
                                  ),
                                ),
                                Text(
                                  '${pl.songCount} songs',
                                  style: const TextStyle(
                                    color: AppColors.textSecondary,
                                    fontSize: 12,
                                  ),
                                ),
                              ],
                            ),
                          );
                        },
                      ),
                    );
                  },
                  loading: () =>
                      const Center(child: CupertinoActivityIndicator()),
                  error: (e, _) => Text('Error: $e'),
                ),
                const SizedBox(height: 16),
                const Divider(),
                const SizedBox(height: 8),
                Text(
                  'Create new playlist',
                  style: Theme.of(context)
                      .textTheme
                      .titleMedium
                      ?.copyWith(fontWeight: FontWeight.w600),
                ),
                const SizedBox(height: 8),
                Row(
                  children: [
                    Expanded(
                      child: CupertinoTextField(
                        controller: _newNameCtrl,
                        placeholder: 'Playlist name',
                      ),
                    ),
                    const SizedBox(width: 8),
                    CupertinoButton.filled(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 16,
                        vertical: 10,
                      ),
                      onPressed: _creating
                          ? null
                          : () async {
                              final name = _newNameCtrl.text.trim();
                              if (name.isEmpty) return;
                              setState(() => _creating = true);
                              try {
                                final pl = await ref
                                    .read(playlistRepositoryProvider)
                                    .createPlaylist(name: name);
                                await ref
                                    .read(playlistRepositoryProvider)
                                    .addSongToPlaylist(pl.id, widget.song.id);
                                ref.invalidate(myPlaylistsProvider);
                                ref.invalidate(playlistSongsProvider(pl.id));
                                if (mounted) {
                                  Navigator.pop(context);
                                  ScaffoldMessenger.of(context).showSnackBar(
                                    SnackBar(
                                      content: Text(
                                        'Created "${pl.name}" and added song',
                                      ),
                                    ),
                                  );
                                }
                              } catch (e) {
                                if (mounted) {
                                  ScaffoldMessenger.of(context).showSnackBar(
                                    SnackBar(content: Text('Failed: $e')),
                                  );
                                }
                              } finally {
                                if (mounted) setState(() => _creating = false);
                              }
                            },
                      child: _creating
                          ? const CupertinoActivityIndicator(
                              color: Colors.white,
                            )
                          : const Text('Create'),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
