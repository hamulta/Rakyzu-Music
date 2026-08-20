import 'dart:ui';

import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../features/player/providers/player_provider.dart';
import '../theme/app_colors.dart';
import 'signed_image.dart';

/// Persistent mini player — muncul di atas bottom nav bar saat ada track aktif.
///
/// Menampilkan: cover art kecil, judul + artist, tombol play/pause, progress bar.
/// Tap → expand ke full player (saat v0.3.3).
class GlassMiniPlayer extends ConsumerWidget {
  const GlassMiniPlayer({super.key, this.onTapFullPlayer});

  final VoidCallback? onTapFullPlayer;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final playerState = ref.watch(playerControllerProvider);
    if (!playerState.hasTrack) return const SizedBox.shrink();

    final track = playerState.currentTrack!;
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return GestureDetector(
      onTap: onTapFullPlayer,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
        child: ClipRRect(
          borderRadius: BorderRadius.circular(20),
          child: BackdropFilter(
            filter: ImageFilter.blur(sigmaX: 20, sigmaY: 20),
            child: Material(
              color: Colors.transparent,
              child: Container(
                padding: const EdgeInsets.all(10),
                decoration: BoxDecoration(
                  gradient: isDark
                      ? AppColors.glassGradientDark
                      : AppColors.glassGradientLight,
                  border: Border.all(color: AppColors.getBorderGlass(isDark)),
                  borderRadius: BorderRadius.circular(20),
                ),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Row(
                      children: [
                        // Cover art.
                        ClipRRect(
                          borderRadius: BorderRadius.circular(10),
                          child: SignedImage(
                            value: track.coverUrl,
                            width: 44,
                            height: 44,
                            fallbackIcon: CupertinoIcons.music_note,
                          ),
                        ),
                        const SizedBox(width: 12),
                        // Title + Artist.
                        Expanded(
                          child: Column(
                            mainAxisSize: MainAxisSize.min,
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                track.title,
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: TextStyle(
                                  fontSize: 14,
                                  fontWeight: FontWeight.w600,
                                  color: AppColors.getTextPrimary(isDark),
                                ),
                              ),
                              Text(
                                track.artistName ?? 'Unknown Artist',
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: TextStyle(
                                  fontSize: 12,
                                  color: AppColors.getTextSecondary(isDark),
                                ),
                              ),
                            ],
                          ),
                        ),
                        // Play/Pause button.
                        _MiniPlayButton(
                          isPlaying: playerState.isPlaying,
                          isLoading: playerState.isLoading,
                          onPressed: () => ref
                              .read(playerControllerProvider.notifier)
                              .togglePlay(),
                        ),
                      ],
                    ),
                    // Thin progress bar.
                    const SizedBox(height: 8),
                    _MiniProgressBar(
                      position: playerState.position,
                      duration: playerState.duration,
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _MiniPlayButton extends StatelessWidget {
  const _MiniPlayButton({
    required this.isPlaying,
    required this.isLoading,
    required this.onPressed,
  });

  final bool isPlaying;
  final bool isLoading;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    if (isLoading) {
      return const SizedBox(
        width: 36,
        height: 36,
        child: Padding(
          padding: EdgeInsets.all(8),
          child: CircularProgressIndicator(strokeWidth: 2),
        ),
      );
    }

    return IconButton.filled(
      onPressed: onPressed,
      style: IconButton.styleFrom(
        backgroundColor: AppColors.azureMistDeep,
        foregroundColor: Colors.white,
      ),
      icon: Icon(
        isPlaying ? CupertinoIcons.pause_fill : CupertinoIcons.play_fill,
        size: 20,
      ),
    );
  }
}

class _MiniProgressBar extends StatelessWidget {
  const _MiniProgressBar({
    required this.position,
    required this.duration,
  });

  final Duration position;
  final Duration duration;

  @override
  Widget build(BuildContext context) {
    final maxMs = duration.inMilliseconds.toDouble().clamp(1, double.infinity);
    final value = duration.inMilliseconds == 0
        ? 0.0
        : position.inMilliseconds.toDouble().clamp(0.0, maxMs);

    return ClipRRect(
      borderRadius: BorderRadius.circular(2),
      child: LinearProgressIndicator(
        value: value / maxMs,
        minHeight: 2,
        backgroundColor: AppColors.azureMistDeep.withOpacity(0.15),
        valueColor:
            const AlwaysStoppedAnimation<Color>(AppColors.azureMistDeep),
      ),
    );
  }
}
