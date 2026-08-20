import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';

import '../../../../core/theme/app_colors.dart';
import '../../../../core/widgets/glass_card.dart';

class LibraryTab extends StatelessWidget {
  const LibraryTab({super.key});

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return Scaffold(
      body: DecoratedBox(
        decoration: BoxDecoration(
          gradient: isDark
              ? AppColors.darkBackgroundGradient
              : AppColors.lightBackgroundGradient,
        ),
        child: SafeArea(
          child: ListView(
            padding: const EdgeInsets.all(20),
            children: [
              Text(
                'Library',
                style: Theme.of(context).textTheme.headlineLarge,
              ),
              const SizedBox(height: 20),
              const _LibraryItem(
                icon: CupertinoIcons.heart_fill,
                label: 'Liked Songs',
              ),
              const _LibraryItem(
                icon: CupertinoIcons.music_note_list,
                label: 'Playlists',
              ),
              const _LibraryItem(
                icon: CupertinoIcons.clock_fill,
                label: 'Recently Played',
              ),
              const _LibraryItem(
                icon: CupertinoIcons.person_2_fill,
                label: 'Artists',
              ),
              const _LibraryItem(
                icon: CupertinoIcons.tray_fill,
                label: 'Albums',
              ),
              const SizedBox(height: 24),
              Text(
                'Fitur Library penuh hadir di v0.5.x',
                textAlign: TextAlign.center,
                style: Theme.of(context).textTheme.bodySmall,
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _LibraryItem extends StatelessWidget {
  const _LibraryItem({required this.icon, required this.label});

  final IconData icon;
  final String label;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: GlassCard(
        borderRadius: 20,
        padding: const EdgeInsets.all(16),
        child: Row(
          children: [
            Icon(icon, color: AppColors.azureMistDeep, size: 22),
            const SizedBox(width: 14),
            Text(label, style: Theme.of(context).textTheme.titleMedium),
            const Spacer(),
            const Icon(
              CupertinoIcons.chevron_right,
              color: AppColors.textSecondary,
            ),
          ],
        ),
      ),
    );
  }
}
