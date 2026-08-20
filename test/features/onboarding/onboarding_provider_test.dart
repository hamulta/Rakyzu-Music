import 'package:flutter_test/flutter_test.dart';
import 'package:rakyzu_music/features/onboarding/providers/onboarding_provider.dart';

void main() {
  group('OnboardingController', () {
    test('initial state has no selected genres and not completed', () {
      final controller = OnboardingController();
      expect(controller.state.selectedGenres, isEmpty);
      expect(controller.state.isCompleted, isFalse);
    });

    test('toggleGenre adds a genre', () {
      final controller = OnboardingController();
      controller.toggleGenre('Pop');
      expect(controller.state.selectedGenres, ['Pop']);
    });

    test('toggleGenre removes an already-selected genre', () {
      final controller = OnboardingController();
      controller.toggleGenre('Pop');
      controller.toggleGenre('Rock');
      controller.toggleGenre('Pop');
      expect(controller.state.selectedGenres, ['Rock']);
    });

    test('toggleGenre respects max selection limit', () {
      final controller = OnboardingController();
      for (var i = 0; i < 10; i++) {
        controller.toggleGenre('Genre $i');
      }
      expect(controller.state.selectedGenres.length, 5);
    });

    test('copyWith preserves unmodified fields', () {
      const state = OnboardingState(selectedGenres: ['Pop'], isCompleted: true);
      final updated = state.copyWith(selectedGenres: ['Pop', 'Rock']);
      expect(updated.selectedGenres, ['Pop', 'Rock']);
      expect(updated.isCompleted, isTrue);
    });
  });
}
