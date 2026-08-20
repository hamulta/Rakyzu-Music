import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/constants/app_constants.dart';
import '../../../../core/constants/app_routes.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/widgets/glass_button.dart';
import '../../../../core/widgets/glass_card.dart';
import '../../providers/onboarding_provider.dart';

/// Seed genres used during onboarding. In later phases these will be
/// sourced from the `genres` table in Supabase.
const List<String> kSeedGenres = [
  'Pop',
  'Rock',
  'Hip-Hop',
  'Electronic',
  'Jazz',
  'R&B',
  'K-Pop',
  'Indie',
  'Classical',
  'Metal',
  'Reggae',
  'Folk',
  'Dangdut',
  'Jazz Lounge',
];

class OnboardingScreen extends ConsumerStatefulWidget {
  const OnboardingScreen({super.key});

  @override
  ConsumerState<OnboardingScreen> createState() => _OnboardingScreenState();
}

class _OnboardingScreenState extends ConsumerState<OnboardingScreen> {
  @override
  Widget build(BuildContext context) {
    final onboarding = ref.watch(onboardingProvider);
    final selected = onboarding.selectedGenres;
    final canProceed = selected.length >= AppConstants.minGenreSelection;
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return Scaffold(
      body: DecoratedBox(
        decoration: BoxDecoration(
          gradient: isDark
              ? AppColors.darkBackgroundGradient
              : AppColors.lightBackgroundGradient,
        ),
        child: SafeArea(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                const SizedBox(height: 16),
                const Icon(
                  CupertinoIcons.music_note_2,
                  size: 56,
                  color: AppColors.azureMistDeep,
                ),
                const SizedBox(height: 16),
                Text(
                  'Pilih Genre Favoritmu',
                  textAlign: TextAlign.center,
                  style: Theme.of(context).textTheme.headlineMedium,
                ),
                const SizedBox(height: 8),
                Text(
                  'Pilih minimal ${AppConstants.minGenreSelection} genre '
                  'untuk rekomendasi musik yang sesuai seleramu.',
                  textAlign: TextAlign.center,
                  style: Theme.of(context).textTheme.bodyMedium,
                ),
                const SizedBox(height: 24),
                Expanded(
                  child: GridView.builder(
                    gridDelegate:
                        const SliverGridDelegateWithFixedCrossAxisCount(
                      crossAxisCount: 2,
                      mainAxisSpacing: 12,
                      crossAxisSpacing: 12,
                      childAspectRatio: 2.4,
                    ),
                    itemCount: kSeedGenres.length,
                    itemBuilder: (context, index) {
                      final genre = kSeedGenres[index];
                      final isSelected = selected.contains(genre);
                      return _GenreChip(
                        label: genre,
                        isSelected: isSelected,
                        onTap: () => ref
                            .read(onboardingProvider.notifier)
                            .toggleGenre(genre),
                      );
                    },
                  ),
                ),
                const SizedBox(height: 16),
                GlassButton(
                  label: canProceed
                      ? 'Lanjut (${selected.length} dipilih)'
                      : 'Pilih minimal ${AppConstants.minGenreSelection} genre',
                  isPrimary: true,
                  onPressed: canProceed ? _complete : null,
                ),
                const SizedBox(height: 8),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Future<void> _complete() async {
    await ref.read(onboardingProvider.notifier).completeOnboarding();
    if (!mounted) return;
    context.go(AppRoutes.main);
  }
}

class _GenreChip extends StatelessWidget {
  const _GenreChip({
    required this.label,
    required this.isSelected,
    required this.onTap,
  });

  final String label;
  final bool isSelected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: GlassCard(
        borderRadius: 20,
        padding: const EdgeInsets.symmetric(horizontal: 16),
        child: Row(
          children: [
            Icon(
              isSelected
                  ? CupertinoIcons.checkmark_circle_fill
                  : CupertinoIcons.circle,
              color: isSelected
                  ? AppColors.azureMistDeep
                  : AppColors.textSecondary,
              size: 22,
            ),
            const SizedBox(width: 10),
            Expanded(
              child: Text(
                label,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: TextStyle(
                  fontSize: 14,
                  fontWeight: isSelected ? FontWeight.w600 : FontWeight.w400,
                  color: AppColors.getTextPrimary(
                    Theme.of(context).brightness == Brightness.dark,
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
