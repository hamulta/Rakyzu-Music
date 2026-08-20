import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:just_audio/just_audio.dart';

import '../../features/catalog/data/r2_storage_service.dart';
import '../providers/audio_player_provider.dart';
import '../theme/app_colors.dart';
import 'glass_card.dart';

/// Pemutar audio untuk lagu yang disimpan di R2 (key diambil via signed URL).
///
/// - `audioKey == null` → state kosong ("Audio belum diupload").
/// - Saat tombol play ditekan, signed URL diambil dari Worker lalu dimuat ke
///   `audioPlayerProvider` dan diputar.
class SignedAudioPlayer extends ConsumerStatefulWidget {
  const SignedAudioPlayer({
    super.key,
    required this.audioKey,
    this.title,
  });

  final String? audioKey;
  final String? title;

  @override
  ConsumerState<SignedAudioPlayer> createState() => _SignedAudioPlayerState();
}

class _SignedAudioPlayerState extends ConsumerState<SignedAudioPlayer> {
  @override
  Widget build(BuildContext context) {
    final audioKey = widget.audioKey;
    if (audioKey == null) {
      return const _PlayerCard(
        child: _EmptyPlayerHint(text: 'Audio belum diupload'),
      );
    }

    final signedUrl = ref.watch(signedAudioUrlProvider(audioKey));
    return signedUrl.when(
      loading: () => const _PlayerCard(
        child: _LoadingPlayer(),
      ),
      error: (error, _) => _PlayerCard(
        child: _EmptyPlayerHint(text: 'Gagal memuat audio: $error'),
      ),
      data: (url) {
        final player = ref.watch(audioPlayerProvider);
        final isActive = ref.watch(activeAudioKeyProvider) == audioKey;

        return StreamBuilder<PlayerState>(
          stream: player.playerStateStream,
          builder: (context, snapshot) {
            final state = snapshot.data;
            final playing = isActive &&
                (state?.playing ?? false) &&
                !(state?.processingState == ProcessingState.completed);
            return _PlayerCard(
              child: Row(
                children: [
                  _PlayButton(
                    playing: playing,
                    onPressed: () => _toggle(url, audioKey, playing),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          widget.title ?? audioKey.split('/').last,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                            fontSize: 13,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                        const SizedBox(height: 4),
                        _PositionSlider(player: player, isActive: isActive),
                      ],
                    ),
                  ),
                ],
              ),
            );
          },
        );
      },
    );
  }

  Future<void> _toggle(String url, String audioKey, bool playing) async {
    if (playing) {
      await ref.read(audioPlayerProvider).pause();
      return;
    }
    if (ref.read(activeAudioKeyProvider) == audioKey) {
      await ref.read(audioPlayerProvider).play();
      return;
    }
    final ok = await playAudioSource(ref, url);
    if (ok) {
      ref.read(activeAudioKeyProvider.notifier).state = audioKey;
    } else if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Gagal memutar audio.')),
      );
    }
  }
}

class _PlayButton extends StatelessWidget {
  const _PlayButton({required this.playing, required this.onPressed});

  final bool playing;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    return IconButton.filled(
      onPressed: onPressed,
      style: IconButton.styleFrom(
        backgroundColor: AppColors.azureMistDeep,
        foregroundColor: Colors.white,
      ),
      icon:
          Icon(playing ? CupertinoIcons.pause_fill : CupertinoIcons.play_fill),
    );
  }
}

class _PositionSlider extends StatelessWidget {
  const _PositionSlider({required this.player, required this.isActive});

  final AudioPlayer player;
  final bool isActive;

  @override
  Widget build(BuildContext context) {
    return StreamBuilder<Duration>(
      stream: player.positionStream,
      builder: (context, posSnapshot) {
        final position = posSnapshot.data ?? Duration.zero;
        return StreamBuilder<Duration?>(
          stream: player.durationStream,
          builder: (context, durSnapshot) {
            final duration =
                isActive ? (durSnapshot.data ?? Duration.zero) : Duration.zero;
            return Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Slider(
                  value: duration.inMilliseconds == 0
                      ? 0
                      : position.inMilliseconds
                          .clamp(0, duration.inMilliseconds)
                          .toDouble(),
                  max: duration.inMilliseconds
                      .toDouble()
                      .clamp(1, double.infinity),
                  onChanged: isActive
                      ? (value) {
                          player.seek(Duration(milliseconds: value.round()));
                        }
                      : null,
                ),
                Text(
                  isActive
                      ? '${_fmt(position)} / ${_fmt(duration)}'
                      : 'Belum diputar',
                  style: const TextStyle(
                    fontSize: 11,
                    color: AppColors.textSecondary,
                  ),
                ),
              ],
            );
          },
        );
      },
    );
  }

  String _fmt(Duration d) {
    final m = d.inMinutes.toString().padLeft(2, '0');
    final s = (d.inSeconds % 60).toString().padLeft(2, '0');
    return '$m:$s';
  }
}

class _PlayerCard extends StatelessWidget {
  const _PlayerCard({required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    return GlassCard(
      borderRadius: 14,
      padding: const EdgeInsets.all(14),
      child: child,
    );
  }
}

class _LoadingPlayer extends StatelessWidget {
  const _LoadingPlayer();

  @override
  Widget build(BuildContext context) {
    return const Row(
      children: [
        SizedBox(
          width: 24,
          height: 24,
          child: CircularProgressIndicator(strokeWidth: 2),
        ),
        SizedBox(width: 12),
        Text(
          'Menyiapkan audio...',
          style: TextStyle(fontSize: 13, color: AppColors.textSecondary),
        ),
      ],
    );
  }
}

class _EmptyPlayerHint extends StatelessWidget {
  const _EmptyPlayerHint({required this.text});

  final String text;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        const Icon(
          CupertinoIcons.music_note,
          size: 20,
          color: AppColors.textSecondary,
        ),
        const SizedBox(width: 12),
        Expanded(
          child: Text(
            text,
            style:
                const TextStyle(fontSize: 13, color: AppColors.textSecondary),
          ),
        ),
      ],
    );
  }
}
