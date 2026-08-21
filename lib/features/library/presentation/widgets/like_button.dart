import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/theme/app_colors.dart';
import '../../../catalog/providers/catalog_providers.dart';

class LikeButton extends ConsumerWidget {
  const LikeButton({super.key, required this.songId, this.size = 22});

  final String songId;
  final double size;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final likedAsync = ref.watch(isSongLikedProvider(songId));
    final isLiked = likedAsync.valueOrNull ?? false;
    return GestureDetector(
      onTap: () async {
        final repo = ref.read(catalogRepositoryProvider);
        try {
          if (isLiked) {
            await repo.unlikeSong(songId);
          } else {
            await repo.likeSong(songId);
          }
          ref.invalidate(isSongLikedProvider(songId));
          ref.invalidate(likedSongsProvider);
          ref.invalidate(likedSongsCountProvider);
        } catch (_) {}
      },
      child: Icon(
        isLiked ? CupertinoIcons.heart_fill : CupertinoIcons.heart,
        size: size,
        color: isLiked ? Colors.pinkAccent : AppColors.textSecondary,
      ),
    );
  }
}
