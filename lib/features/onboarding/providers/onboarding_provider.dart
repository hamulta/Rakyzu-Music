import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Represents the current onboarding state for the user.
class OnboardingState {
  const OnboardingState({
    this.selectedGenres = const [],
    this.isCompleted = false,
  });

  final List<String> selectedGenres;
  final bool isCompleted;

  OnboardingState copyWith({
    List<String>? selectedGenres,
    bool? isCompleted,
  }) {
    return OnboardingState(
      selectedGenres: selectedGenres ?? this.selectedGenres,
      isCompleted: isCompleted ?? this.isCompleted,
    );
  }
}

/// Provider holding the in-memory onboarding state.
final onboardingProvider =
    StateNotifierProvider<OnboardingController, OnboardingState>((ref) {
  return OnboardingController();
});

class OnboardingController extends StateNotifier<OnboardingState> {
  OnboardingController() : super(const OnboardingState());

  void toggleGenre(String genre, {int minSelection = 3, int maxSelection = 5}) {
    final current = state.selectedGenres;
    if (current.contains(genre)) {
      state = state.copyWith(
        selectedGenres: current.where((g) => g != genre).toList(),
      );
    } else {
      if (current.length >= maxSelection) return;
      state = state.copyWith(
        selectedGenres: [...current, genre],
      );
    }
  }

  Future<void> completeOnboarding() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setStringList('selected_genres', state.selectedGenres);
    await prefs.setBool('onboarding_done', true);
    state = state.copyWith(isCompleted: true);
  }

  Future<void> load() async {
    final prefs = await SharedPreferences.getInstance();
    final genres = prefs.getStringList('selected_genres') ?? [];
    final completed = prefs.getBool('onboarding_done') ?? false;
    state = OnboardingState(
      selectedGenres: genres,
      isCompleted: completed,
    );
  }
}
