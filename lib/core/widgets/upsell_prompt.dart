import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../theme/app_colors.dart';

class UpsellPrompt extends StatelessWidget {
  const UpsellPrompt({super.key, this.reason = 'ads'});
  final String reason; // 'ads' | 'skip'

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final title = reason == 'skip' ? 'Skip limit reached' : 'Enjoyed the ad?';
    final subtitle = reason == 'skip'
        ? 'You reached 6 skips/hour on Free. Upgrade for unlimited skips.'
        : 'Upgrade to Premium for ad-free listening & unlimited skips.';

    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        gradient: LinearGradient(
          colors: isDark
              ? [AppColors.darkSurfaceElevated, AppColors.darkCard]
              : [Colors.white, AppColors.ivoryBase],
        ),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: AppColors.getBorderGlass(isDark)),
        boxShadow: [
          BoxShadow(
              color: Colors.black.withOpacity(0.08),
              blurRadius: 12,
              offset: const Offset(0, 4)),
        ],
      ),
      child: Row(
        children: [
          Container(
            padding: const EdgeInsets.all(8),
            decoration: BoxDecoration(
                color: AppColors.premiumGradient.colors.first.withOpacity(0.15),
                shape: BoxShape.circle),
            child: const Icon(CupertinoIcons.star_fill,
                size: 18, color: Color(0xFFFFA500)),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(title,
                    style: const TextStyle(
                        fontWeight: FontWeight.w700, fontSize: 13)),
                const SizedBox(height: 2),
                Text(subtitle,
                    style: const TextStyle(
                        color: AppColors.textSecondary, fontSize: 12)),
              ],
            ),
          ),
          const SizedBox(width: 12),
          CupertinoButton(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
            color: AppColors.azureMistDeep,
            borderRadius: BorderRadius.circular(20),
            onPressed: () => context.push('/premium/upgrade'),
            child: const Text('Upgrade',
                style: TextStyle(
                    color: Colors.white,
                    fontSize: 12,
                    fontWeight: FontWeight.w600)),
          ),
        ],
      ),
    );
  }
}
