import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:rakyzu_music/core/providers/audio_player_provider.dart';
import 'package:rakyzu_music/core/widgets/signed_audio_player.dart';

void main() {
  group('AudioPlayerProviders', () {
    test('activeAudioKeyProvider awalnya null', () {
      final container = ProviderContainer();
      addTearDown(container.dispose);

      expect(container.read(activeAudioKeyProvider), isNull);
    });

    test('audioPlayerProvider menyediakan instance AudioPlayer', () {
      final container = ProviderContainer();
      addTearDown(container.dispose);

      final player = container.read(audioPlayerProvider);
      expect(player, isNotNull);
      expect(container.read(audioPlayerProvider), same(player));
    });
  });

  group('SignedAudioPlayer', () {
    testWidgets('menampilkan state kosong saat audioKey null', (tester) async {
      await tester.pumpWidget(
        const ProviderScope(
          child: MaterialApp(
            home: Scaffold(
              body: SignedAudioPlayer(audioKey: null, title: 'Demo'),
            ),
          ),
        ),
      );

      expect(find.text('Audio belum diupload'), findsOneWidget);
      expect(find.text('Demo'), findsNothing);
    });
  });
}
