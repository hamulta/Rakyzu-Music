import 'package:audio_service/audio_service.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'audio_handler.dart';
import 'player_provider.dart';

/// Lazy-initialized AudioHandler.
/// Diinisialisasi pertama kali saat `audioHandlerProvider` dibaca.
final audioHandlerProvider = FutureProvider<RakyzuAudioHandler>((ref) async {
  final controller = ref.watch(playerControllerProvider.notifier);
  final handler = RakyzuAudioHandler(controller.player);
  controller.setAudioHandler(handler);

  final audioHandler = await AudioService.init(
    builder: () => handler,
    config: const AudioServiceConfig(
      androidNotificationChannelId: 'com.hamulta.rakyzu_music.channel.audio',
      androidNotificationChannelName: 'Rakyzu Music',
      androidNotificationOngoing: true,
      androidStopForegroundOnPause: true,
      androidNotificationIcon: 'mipmap/ic_launcher',
      fastForwardInterval: Duration(seconds: 10),
      rewindInterval: Duration(seconds: 10),
    ),
  );

  return audioHandler;
});
