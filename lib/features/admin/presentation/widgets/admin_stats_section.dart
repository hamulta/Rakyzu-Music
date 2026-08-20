import 'package:flutter/material.dart';

import '../../../../core/theme/app_colors.dart';
import '../../../../core/widgets/glass_card.dart';

class AdminStatsSection extends StatelessWidget {
  const AdminStatsSection({
    super.key,
    required this.artists,
    required this.albums,
    required this.songs,
  });

  final int artists;
  final int albums;
  final int songs;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        _StatCard(label: 'Artis', count: artists),
        const SizedBox(width: 12),
        _StatCard(label: 'Album', count: albums),
        const SizedBox(width: 12),
        _StatCard(label: 'Lagu', count: songs),
      ],
    );
  }
}

class _StatCard extends StatelessWidget {
  const _StatCard({required this.label, required this.count});

  final String label;
  final int count;

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: GlassCard(
        borderRadius: 16,
        padding: const EdgeInsets.symmetric(vertical: 16),
        child: Column(
          children: [
            Text(
              '$count',
              style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w700),
            ),
            const SizedBox(height: 4),
            Text(
              label,
              style: const TextStyle(
                fontSize: 13,
                color: AppColors.textSecondary,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
