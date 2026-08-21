import 'package:flutter/material.dart';

import '../theme/app_colors.dart';

/// Placeholder ad-slot untuk Flutter Web.
/// Di-isolasi agar swap ke Google AdSense script mudah
/// (cukup ganti isi widget ini dengan HtmlElementView).
class WebAdSlot extends StatelessWidget {
  const WebAdSlot({super.key, this.height = 90});

  final double height;

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return Container(
      height: height,
      margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      decoration: BoxDecoration(
        color: isDark
            ? AppColors.darkSurfaceElevated
            : Colors.white.withOpacity(0.7),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: AppColors.getBorderGlass(isDark)),
      ),
      child: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.campaign_outlined,
                color: AppColors.textSecondary.withOpacity(0.6)),
            const SizedBox(height: 4),
            Text(
              'Ad Slot — Web (AdSense ready)',
              style: TextStyle(
                fontSize: 12,
                color: AppColors.textSecondary.withOpacity(0.8),
              ),
            ),
            const SizedBox(height: 2),
            Text(
              'Ganti widget ini dengan AdSense script di 0.9.x',
              style: TextStyle(
                fontSize: 10,
                color: AppColors.textSecondary.withOpacity(0.6),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
