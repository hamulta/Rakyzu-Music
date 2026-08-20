import 'dart:ui';
import 'package:flutter/material.dart';

import '../theme/app_colors.dart';

/// Persistent mini player bar shown above the bottom nav.
/// In v0.1.x this is a placeholder; playback engine lands in v0.3.x.
class GlassMiniPlayer extends StatelessWidget {
  const GlassMiniPlayer({
    super.key,
    this.isVisible = false,
    this.onTap,
  });

  final bool isVisible;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    if (!isVisible) return const SizedBox.shrink();

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(20),
        child: BackdropFilter(
          filter: ImageFilter.blur(sigmaX: 20, sigmaY: 20),
          child: Material(
            color: Colors.transparent,
            child: InkWell(
              onTap: onTap,
              child: Container(
                padding: const EdgeInsets.all(10),
                decoration: BoxDecoration(
                  gradient: isDark
                      ? AppColors.glassGradientDark
                      : AppColors.glassGradientLight,
                  border: Border.all(color: AppColors.getBorderGlass(isDark)),
                  borderRadius: BorderRadius.circular(20),
                ),
                child: Row(
                  children: [
                    Container(
                      width: 44,
                      height: 44,
                      decoration: BoxDecoration(
                        color: AppColors.azureMistDeep.withOpacity(0.3),
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: const Icon(
                        Icons.music_note,
                        color: AppColors.azureMistDeep,
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            'No song playing',
                            style: TextStyle(
                              fontSize: 14,
                              fontWeight: FontWeight.w600,
                              color: AppColors.getTextPrimary(isDark),
                            ),
                          ),
                          Text(
                            'Tap to play something',
                            style: TextStyle(
                              fontSize: 12,
                              color: AppColors.getTextSecondary(isDark),
                            ),
                          ),
                        ],
                      ),
                    ),
                    const Icon(
                      Icons.play_circle_fill_rounded,
                      size: 40,
                      color: AppColors.azureMistDeep,
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
