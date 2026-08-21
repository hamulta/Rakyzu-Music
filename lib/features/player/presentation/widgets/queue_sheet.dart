import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/theme/app_colors.dart';
import '../../providers/player_provider.dart';

/// Bottom sheet untuk melihat dan mengelola antrian pemutar.
///
/// Menampilkan daftar track dalam queue, dengan kemampuan reorder (drag-drop)
/// dan hapus track dari queue.
class QueueSheet extends ConsumerWidget {
  const QueueSheet({super.key});

  static void show(BuildContext context) {
    showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => const QueueSheet(),
    );
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final playerState = ref.watch(playerControllerProvider);
    final controller = ref.read(playerControllerProvider.notifier);
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return DraggableScrollableSheet(
      initialChildSize: 0.6,
      minChildSize: 0.3,
      maxChildSize: 0.9,
      builder: (context, scrollController) {
        return Container(
          decoration: BoxDecoration(
            color: isDark ? const Color(0xFF1A1B2E) : Colors.white,
            borderRadius: const BorderRadius.vertical(top: Radius.circular(20)),
          ),
          child: Column(
            children: [
              // Handle bar.
              Container(
                margin: const EdgeInsets.only(top: 12),
                width: 40,
                height: 4,
                decoration: BoxDecoration(
                  color: AppColors.textSecondary.withOpacity(0.3),
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
              // Header.
              Padding(
                padding: const EdgeInsets.all(16),
                child: Row(
                  children: [
                    const Expanded(
                      child: Text(
                        'Queue',
                        style: TextStyle(
                          fontSize: 18,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ),
                    Text(
                      '${playerState.queue.length} songs',
                      style: const TextStyle(
                        fontSize: 14,
                        color: AppColors.textSecondary,
                      ),
                    ),
                    const SizedBox(width: 8),
                    IconButton(
                      onPressed: () => Navigator.of(context).pop(),
                      icon: const Icon(CupertinoIcons.xmark, size: 20),
                    ),
                  ],
                ),
              ),
              const Divider(height: 1),
              // Queue list.
              Expanded(
                child: playerState.queue.isEmpty
                    ? const Center(
                        child: Text(
                          'Queue kosong',
                          style: TextStyle(color: AppColors.textSecondary),
                        ),
                      )
                    : ReorderableListView.builder(
                        scrollController: scrollController,
                        itemCount: playerState.queue.length,
                        onReorder: (oldIndex, newIndex) {
                          controller.reorderQueue(oldIndex, newIndex);
                        },
                        itemBuilder: (context, index) {
                          final song = playerState.queue[index];
                          final isCurrentTrack =
                              song.id == playerState.currentTrack?.id;

                          return _QueueTile(
                            key: ValueKey(song.id + index.toString()),
                            song: song,
                            index: index,
                            isCurrentTrack: isCurrentTrack,
                            isPlaying: isCurrentTrack && playerState.isPlaying,
                            onRemove: () => controller.removeFromQueue(index),
                            onTap: () => controller.skipToIndex(index),
                          );
                        },
                      ),
              ),
            ],
          ),
        );
      },
    );
  }
}

class _QueueTile extends StatelessWidget {
  const _QueueTile({
    super.key,
    required this.song,
    required this.index,
    required this.isCurrentTrack,
    required this.isPlaying,
    required this.onRemove,
    required this.onTap,
  });

  final dynamic song;
  final int index;
  final bool isCurrentTrack;
  final bool isPlaying;
  final VoidCallback onRemove;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Dismissible(
      key: key!,
      direction: DismissDirection.endToStart,
      onDismissed: (_) => onRemove(),
      background: Container(
        alignment: Alignment.centerRight,
        padding: const EdgeInsets.only(right: 20),
        color: Colors.red,
        child: const Icon(CupertinoIcons.trash, color: Colors.white),
      ),
      child: ListTile(
        leading: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (isCurrentTrack)
              Icon(
                isPlaying
                    ? CupertinoIcons.speaker_fill
                    : CupertinoIcons.speaker_2_fill,
                size: 16,
                color: AppColors.azureMistDeep,
              )
            else
              Text(
                '${index + 1}',
                style: const TextStyle(
                  fontSize: 14,
                  color: AppColors.textSecondary,
                ),
              ),
            const SizedBox(width: 8),
          ],
        ),
        title: Text(
          song.title,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: TextStyle(
            fontSize: 14,
            fontWeight: isCurrentTrack ? FontWeight.w600 : FontWeight.normal,
            color: isCurrentTrack ? AppColors.azureMistDeep : null,
          ),
        ),
        subtitle: Text(
          song.artistName ?? 'Unknown Artist',
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: const TextStyle(
            fontSize: 12,
            color: AppColors.textSecondary,
          ),
        ),
        trailing: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            IconButton(
              onPressed: onRemove,
              icon: const Icon(
                CupertinoIcons.xmark,
                size: 16,
                color: AppColors.textSecondary,
              ),
            ),
            const Icon(CupertinoIcons.line_horizontal_3, size: 16),
          ],
        ),
        onTap: onTap,
      ),
    );
  }
}
