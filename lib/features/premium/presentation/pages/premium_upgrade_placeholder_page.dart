import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/theme/app_colors.dart';
import '../../../../core/widgets/glass_card.dart';

class PremiumUpgradePlaceholderPage extends StatelessWidget {
  const PremiumUpgradePlaceholderPage({super.key});

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return Scaffold(
      body: DecoratedBox(
        decoration: BoxDecoration(gradient: isDark ? AppColors.darkBackgroundGradient : AppColors.lightBackgroundGradient),
        child: SafeArea(
          child: ListView(
            padding: const EdgeInsets.all(20),
            children: [
              Row(
                children: [
                  IconButton(onPressed: () => context.pop(), icon: const Icon(CupertinoIcons.back)),
                  Text('Upgrade to Premium', style: Theme.of(context).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w700)),
                ],
              ),
              const SizedBox(height: 24),
              GlassCard(
                padding: const EdgeInsets.all(24),
                child: Column(
                  children: [
                    Container(
                      width: 72,
                      height: 72,
                      decoration: const BoxDecoration(shape: BoxShape.circle, gradient: AppColors.premiumGradient),
                      child: const Icon(CupertinoIcons.star_fill, color: Colors.white, size: 32),
                    ),
                    const SizedBox(height: 16),
                    Text('Your Sound, Without Limits.', style: Theme.of(context).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w800), textAlign: TextAlign.center),
                    const SizedBox(height: 8),
                    const Text('Premium — Coming Soon in 0.7.x\nAd-free • Unlimited skips • Offline download', textAlign: TextAlign.center, style: TextStyle(color: AppColors.textSecondary)),
                    const SizedBox(height: 20),
                    SizedBox(
                      width: double.infinity,
                      child: CupertinoButton.filled(onPressed: () => context.pop(), child: const Text('Back to Library')),
                    ),
                    const SizedBox(height: 8),
                    Text('Payment via Midtrans akan aktif di 0.7.x', style: TextStyle(color: AppColors.textSecondary.withOpacity(0.7), fontSize: 12), textAlign: TextAlign.center),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
