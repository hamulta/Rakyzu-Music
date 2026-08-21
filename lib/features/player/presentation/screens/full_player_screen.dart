import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/ads/ads_gate_provider.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/widgets/signed_image.dart';
import '../../../../core/widgets/upsell_prompt.dart';
import '../../providers/player_controller.dart';
import '../../providers/player_provider.dart';

/// Full-screen player — swipe-up dari mini player atau navigasi eksplisit.
///
/// Menampilkan: cover art besar, judul + artist, seek slider,
/// kontrol play/pause/skip, dan tombol like.
class FullPlayerScreen extends ConsumerStatefulWidget {
  const FullPlayerScreen({super.key});

  @override
  ConsumerState<FullPlayerScreen> createState() => _FullPlayerScreenState();
}

class _FullPlayerScreenState extends ConsumerState<FullPlayerScreen> {
  bool _showLyrics = false;

  @override
  Widget build(BuildContext context) {
    final playerState = ref.watch(playerControllerProvider);
    final track = playerState.currentTrack;
    final isDark = Theme.of(context).brightness == Brightness.dark;

    if (track == null) {
      return const Scaffold(
        body: Center(child: Text('Tidak ada lagu yang diputar')),
      );
    }

    return Scaffold(
      body: DecoratedBox(
        decoration: BoxDecoration(
          gradient: isDark
              ? AppColors.darkBackgroundGradient
              : AppColors.lightBackgroundGradient,
        ),
        child: SafeArea(
          child: Column(
            children: [
              _buildHeader(context, track),
              Expanded(
                child: _showLyrics
                    ? _LyricsView(lyrics: track.lyrics)
                    : _buildMainContent(context, playerState, isDark),
              ),
              _buildControls(context, playerState, isDark),
              const SizedBox(height: 20),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildHeader(BuildContext context, dynamic track) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      child: Row(
        children: [
          IconButton(
            onPressed: () => Navigator.of(context).pop(),
            icon: const Icon(CupertinoIcons.chevron_down, size: 28),
          ),
          Expanded(
            child: Text(
              'Now Playing',
              textAlign: TextAlign.center,
              style: Theme.of(context).textTheme.titleMedium?.copyWith(
                    fontWeight: FontWeight.w600,
                  ),
            ),
          ),
          IconButton(
            onPressed: () => setState(() => _showLyrics = !_showLyrics),
            icon: Icon(
              _showLyrics
                  ? CupertinoIcons.music_note
                  : CupertinoIcons.text_alignleft,
              size: 22,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildMainContent(
    BuildContext context,
    PlaybackState playerState,
    bool isDark,
  ) {
    final track = playerState.currentTrack!;

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 32),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          // Cover art.
          ClipRRect(
            borderRadius: BorderRadius.circular(20),
            child: SignedImage(
              value: track.coverUrl,
              width: 280,
              height: 280,
              fallbackIcon: CupertinoIcons.music_note_2,
            ),
          ),
          const SizedBox(height: 32),
          // Title.
          Text(
            track.title,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            textAlign: TextAlign.center,
            style: const TextStyle(
              fontSize: 22,
              fontWeight: FontWeight.bold,
            ),
          ),
          const SizedBox(height: 8),
          // Artist.
          Text(
            track.artistName ?? 'Unknown Artist',
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            textAlign: TextAlign.center,
            style: const TextStyle(
              fontSize: 16,
              color: AppColors.textSecondary,
            ),
          ),
          const SizedBox(height: 24),
          // Seek slider.
          _SeekSlider(
            position: playerState.position,
            duration: playerState.duration,
            onSeek: (pos) =>
                ref.read(playerControllerProvider.notifier).seek(pos),
          ),
          const SizedBox(height: 8),
          // Like button (placeholder).
          Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              IconButton(
                onPressed: () {
                  // TODO(v0.4.x): Connect to liked_songs.
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(
                      content: Text('Like functionality coming soon!'),
                      duration: Duration(seconds: 1),
                    ),
                  );
                },
                icon: const Icon(
                  CupertinoIcons.heart,
                  size: 24,
                  color: AppColors.textSecondary,
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildControls(
    BuildContext context,
    PlaybackState playerState,
    bool isDark,
  ) {
    final controller = ref.read(playerControllerProvider.notifier);
    final gate = ref.watch(adsGateProvider);
    final isLimitReached =
        gate.shouldEnforceSkipLimit && controller.isSkipLimitReached;

    Future<void> handleSkip(Future<void> Function() action) async {
      if (gate.shouldEnforceSkipLimit) {
        if (controller.isSkipLimitReached) {
          if (!context.mounted) return;
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(
                  'Skip limit 6/jam tercapai. Reset dalam ${controller.skipTimeUntilReset.inMinutes} menit. Upgrade untuk unlimited.'),
              action: SnackBarAction(
                label: 'Upgrade',
                onPressed: () => _showUpsell(context, 'skip'),
              ),
            ),
          );
          return;
        }
        final allowed = controller.tryConsumeSkip();
        if (!allowed) {
          if (!context.mounted) return;
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
                content: Text('Skip limit tercapai. Coba lagi nanti.')),
          );
          return;
        }
      }
      await action();
    }

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 24),
      child: Column(
        children: [
          // Skip-limit hint for free.
          if (gate.shouldEnforceSkipLimit)
            Padding(
              padding: const EdgeInsets.only(bottom: 8),
              child: Text(
                isLimitReached
                    ? 'Skip limit reached — ${controller.skipTimeUntilReset.inMinutes}m reset'
                    : 'Skips left: ${controller.skipRemaining}/6 per hour',
                style: TextStyle(
                  fontSize: 11,
                  color: isLimitReached
                      ? AppColors.accentWarning
                      : AppColors.textSecondary,
                ),
              ),
            ),
          // Progress text.
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(
                  _formatDuration(playerState.position),
                  style: const TextStyle(
                    fontSize: 12,
                    color: AppColors.textSecondary,
                  ),
                ),
                Text(
                  _formatDuration(playerState.duration),
                  style: const TextStyle(
                    fontSize: 12,
                    color: AppColors.textSecondary,
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 12),
          // Main controls: prev, play/pause, next.
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceEvenly,
            children: [
              IconButton(
                onPressed: isLimitReached
                    ? null
                    : () => handleSkip(controller.skipPrevious),
                icon: Icon(
                  CupertinoIcons.backward_fill,
                  size: 32,
                  color: isLimitReached
                      ? AppColors.textSecondary.withOpacity(0.35)
                      : null,
                ),
              ),
              _PlayPauseButton(
                isPlaying: playerState.isPlaying,
                isLoading: playerState.isLoading,
                onPressed: controller.togglePlay,
              ),
              IconButton(
                onPressed: isLimitReached
                    ? null
                    : () => handleSkip(controller.skipNext),
                icon: Icon(
                  CupertinoIcons.forward_fill,
                  size: 32,
                  color: isLimitReached
                      ? AppColors.textSecondary.withOpacity(0.35)
                      : null,
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  void _showUpsell(BuildContext context, String reason) {
    showModalBottomSheet<void>(
      context: context,
      backgroundColor: Colors.transparent,
      builder: (_) => UpsellPrompt(reason: reason),
    );
  }

  String _formatDuration(Duration d) {
    final m = d.inMinutes.toString().padLeft(2, '0');
    final s = (d.inSeconds % 60).toString().padLeft(2, '0');
    return '$m:$s';
  }
}

class _SeekSlider extends StatelessWidget {
  const _SeekSlider({
    required this.position,
    required this.duration,
    required this.onSeek,
  });

  final Duration position;
  final Duration duration;
  final ValueChanged<Duration> onSeek;

  @override
  Widget build(BuildContext context) {
    final maxMs =
        duration.inMilliseconds.toDouble().clamp(1.0, double.infinity);
    final value = duration.inMilliseconds == 0
        ? 0.0
        : position.inMilliseconds.toDouble().clamp(0.0, maxMs);

    return Slider(
      value: value,
      max: maxMs,
      onChanged: (v) => onSeek(Duration(milliseconds: v.round())),
      activeColor: AppColors.azureMistDeep,
      inactiveColor: AppColors.azureMistDeep.withOpacity(0.2),
    );
  }
}

class _PlayPauseButton extends StatelessWidget {
  const _PlayPauseButton({
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
        width: 64,
        height: 64,
        child: Center(
          child: CircularProgressIndicator(strokeWidth: 3),
        ),
      );
    }

    return GestureDetector(
      onTap: onPressed,
      child: Container(
        width: 64,
        height: 64,
        decoration: BoxDecoration(
          shape: BoxShape.circle,
          color: AppColors.azureMistDeep,
          boxShadow: [
            BoxShadow(
              color: AppColors.azureMistDeep.withOpacity(0.3),
              blurRadius: 12,
              offset: const Offset(0, 4),
            ),
          ],
        ),
        child: Icon(
          isPlaying ? CupertinoIcons.pause_fill : CupertinoIcons.play_fill,
          color: Colors.white,
          size: 32,
        ),
      ),
    );
  }
}

class _LyricsView extends StatelessWidget {
  const _LyricsView({this.lyrics});

  final String? lyrics;

  @override
  Widget build(BuildContext context) {
    if (lyrics == null || lyrics!.isEmpty) {
      return const Center(
        child: Padding(
          padding: EdgeInsets.all(32),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(
                CupertinoIcons.text_alignleft,
                size: 48,
                color: AppColors.textSecondary,
              ),
              SizedBox(height: 16),
              Text(
                'Lirik belum tersedia',
                style: TextStyle(
                  fontSize: 16,
                  color: AppColors.textSecondary,
                ),
              ),
            ],
          ),
        ),
      );
    }

    return SingleChildScrollView(
      padding: const EdgeInsets.all(32),
      child: Text(
        lyrics!,
        style: const TextStyle(
          fontSize: 18,
          height: 1.8,
          color: AppColors.textSecondary,
        ),
        textAlign: TextAlign.center,
      ),
    );
  }
}
