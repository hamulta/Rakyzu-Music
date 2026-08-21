import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:just_audio/just_audio.dart';
import 'package:rakyzu_music/core/providers/audio_player_provider.dart';
import 'package:rakyzu_music/core/widgets/signed_audio_player.dart';
import 'package:rakyzu_music/features/catalog/data/r2_storage_service.dart';

void main() {
  group('AudioPlayerProviders', () {
    test('activeAudioKeyProvider awalnya null', () {
      final container = ProviderContainer();
      addTearDown(container.dispose);

      expect(container.read(activeAudioKeyProvider), isNull);
    });

    test('audioPlayerProvider menyediakan instance AudioPlayer', () {
      final player = AudioPlayer();
      addTearDown(player.dispose);

      final container = ProviderContainer(
        overrides: [
          audioPlayerProvider.overrideWithValue(player),
        ],
      );
      addTearDown(container.dispose);

      expect(container.read(audioPlayerProvider), same(player));
    });
  });

  group('SignedAudioPlayer', () {
    testWidgets('menampilkan state kosong saat audioKey null', (tester) async {
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            signedAudioUrlProvider.overrideWith(
              (ref, key) =>
                  Future.error('Should not be called for null audioKey'),
            ),
          ],
          child: const MaterialApp(
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
